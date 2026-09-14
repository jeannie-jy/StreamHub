import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { setAccessToken } from '@/api/client'
import { useAuthStore } from './auth'

vi.mock('@/api', () => ({
  authApi: {
    login: vi.fn(async () => ({ userId: 9, accessToken: 'memory-token', expiresAt: '2030-01-01T00:00:00Z' })),
    me: vi.fn(async () => ({ userId: 9, username: 'viewer', nickname: '观众', role: 'USER', expiresAt: '2030-01-01T00:00:00Z' })),
    refresh: vi.fn(),
    logout: vi.fn(),
  },
  userApi: {
    profile: vi.fn(async () => ({ id: 9, username: 'viewer', nickname: '观众', role: 'USER', status: 'ACTIVE', followerCount: 0 })),
  },
}))

describe('auth store', () => {
  beforeEach(() => {
    setAccessToken(null)
    setActivePinia(createPinia())
  })

  it('reacts when a guest logs in and hydrates the profile', async () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)

    await auth.login({ username: 'viewer', password: 'password123' })

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.profile?.nickname).toBe('观众')
    expect(auth.isOperator).toBe(false)
  })
})
