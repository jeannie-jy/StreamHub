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
  let hydratePromise: Promise<void> | null = null

  // `accessToken` lives outside Vue's reactivity system. Reading it first made
  // this computed value cache `false` forever when the app initially rendered
  // as a guest, because the short circuit never tracked `session.value`.
  // The session is populated and cleared together with the in-memory token, so
  // it is the reactive source of truth for the UI and router guards.
  const isAuthenticated = computed(() => Boolean(session.value?.accessToken))
  const isOperator = computed(() => ['OPERATOR', 'ADMIN'].includes(profile.value?.role || session.value?.role || ''))
  const userId = computed(() => session.value?.userId || 0)

  async function login(credentials: { username: string; password: string }) {
    loading.value = true
    try {
      const next = await authApi.login({ username: credentials.username.trim(), password: credentials.password })
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
      const next = await authApi.register({ username: body.username.trim(), nickname: body.nickname.trim(), password: body.password })
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
      return true
    } catch {
      clearSession()
      return false
    }
  }

  async function hydrate() {
    if (hydrated.value) return
    if (hydratePromise) return hydratePromise
    hydratePromise = (async () => {
      if (await refresh()) await loadProfile()
      hydrated.value = true
    })().finally(() => {
      hydratePromise = null
    })
    await hydratePromise
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
    if (!session.value?.userId) return null
    try {
      const next = await userApi.profile(session.value.userId)
      setProfile(next)
      return next
    } catch {
      return null
    }
  }

  function clearSession() {
    session.value = null
    profile.value = null
    setAccessToken(null)
  }

  setRefreshHandler(refresh)

  return { session, profile, loading, hydrated, isAuthenticated, isOperator, userId, login, register, refresh, hydrate, logout, setProfile, clearSession, loadProfile }
})
