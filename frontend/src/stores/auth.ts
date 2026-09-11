import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, userApi } from '@/api'
import { getAccessToken, setAccessToken, setRefreshHandler } from '@/api/client'
import type { AuthSession, UserProfile } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<AuthSession | null>(null)
  const profile = ref<UserProfile | null>(null)
  const loading = ref(false)
  const hydrated = ref(false)

  const isAuthenticated = computed(() => Boolean(getAccessToken() && session.value))
  const isOperator = computed(() => ['OPERATOR', 'ADMIN'].includes(profile.value?.role || session.value?.role || ''))
  const userId = computed(() => session.value?.userId || 0)

  async function login(credentials: { username: string; password: string }) {
    loading.value = true
    try {
      const next = await authApi.login(credentials)
      applySession(next)
      await loadProfile()
      return next
    } finally {
      loading.value = false
    }
  }

  async function register(body: { username: string; nickname: string; password: string }) {
    loading.value = true
    try {
      const next = await authApi.register(body)
      applySession(next)
      await loadProfile()
      return next
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    try {
      const next = await authApi.refresh()
      applySession(next)
      await loadProfile()
      return true
    } catch {
      clearSession()
      return false
    }
  }

  async function hydrate() {
    if (hydrated.value) return
    hydrated.value = true
    await refresh()
  }

  async function logout() {
    try {
      if (getAccessToken()) await authApi.logout()
    } finally {
      clearSession()
    }
  }

  function applySession(next: AuthSession) {
    session.value = next
    setAccessToken(next.accessToken || null)
  }

  function setProfile(next: UserProfile | null) {
    profile.value = next
    if (next && session.value) session.value = { ...session.value, nickname: next.nickname, role: next.role }
  }

  async function loadProfile() {
    try {
      if (session.value?.userId) setProfile(await userApi.profile(session.value.userId))
    } catch {
      // The access token remains usable when profile enrichment is temporarily unavailable.
    }
  }

  function clearSession() {
    session.value = null
    profile.value = null
    setAccessToken(null)
  }

  setRefreshHandler(refresh)

  return { session, profile, loading, hydrated, isAuthenticated, isOperator, userId, login, register, refresh, hydrate, logout, setProfile, clearSession }
})
