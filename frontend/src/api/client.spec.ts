import { describe, expect, it } from 'vitest'
import { ApiError, setAccessToken } from './client'

describe('api client primitives', () => {
  it('stores an access token only in the module memory', async () => {
    setAccessToken('short-lived-token')
    const module = await import('./client')
    expect(module.getAccessToken()).toBe('short-lived-token')
    setAccessToken(null)
    expect(module.getAccessToken()).toBeNull()
  })

  it('exposes structured API failures to the UI layer', () => {
    const error = new ApiError('请求被限流', 'COMMON-429', 429, 'trace-1')
    expect(error.message).toBe('请求被限流')
    expect(error.status).toBe(429)
    expect(error.code).toBe('COMMON-429')
    expect(error.traceId).toBe('trace-1')
  })
})
