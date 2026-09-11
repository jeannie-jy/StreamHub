import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  timeout: 12_000,
  headers: { 'Content-Type': 'application/json' },
})

let accessToken: string | null = null
let refreshHandler: (() => Promise<boolean>) | null = null
let refreshPromise: Promise<boolean> | null = null

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code = 'COMMON-500',
    public readonly status = 500,
    public readonly traceId = '-',
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export function setRefreshHandler(handler: (() => Promise<boolean>) | null) {
  refreshHandler = handler
}

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const original = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status
    const shouldRefresh = status === 401 && original && !original._retry && !String(original.url).includes('/v1/auth/refresh')
    if (shouldRefresh && refreshHandler) {
      original._retry = true
      refreshPromise ??= refreshHandler().finally(() => {
        refreshPromise = null
      })
      if (await refreshPromise) {
        return http(original)
      }
    }
    return Promise.reject(error)
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiResponse<T>>(config)
    const payload = response.data
    if (!payload.success) {
      throw new ApiError(payload.message, payload.code, response.status, payload.traceId)
    }
    return payload.data
  } catch (error) {
    if (error instanceof ApiError) throw error
    const axiosError = error as AxiosError<ApiResponse<unknown>>
    const payload = axiosError.response?.data
    throw new ApiError(
      payload?.message || axiosError.message || '网络请求失败',
      payload?.code || `HTTP-${axiosError.response?.status || 500}`,
      axiosError.response?.status || 500,
      payload?.traceId || '-',
    )
  }
}

export default http
