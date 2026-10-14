import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResult, TokenVO } from '@/types/api'

declare module 'axios' {
  interface AxiosRequestConfig { skipAuthReplay?: boolean }
}

export class ApiError extends Error {
  code?: number
  constructor(message: string, code?: number) { super(message); this.name = 'ApiError'; this.code = code }
}

export function unwrapApiResult<T>(result: ApiResult<T>): T {
  if (result.code !== 1) throw new ApiError(result.msg || '请求失败', result.code)
  return result.data as T
}

type RetryableRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean }
export type RefreshTokenRequest = (refreshToken: string) => Promise<TokenVO>

const authStorageKeys = ['token', 'refreshToken', 'tokenType', 'accessTtl', 'userInfo'] as const

function saveTokens(tokens: TokenVO) {
  sessionStorage.setItem('token', tokens.accessToken)
  sessionStorage.setItem('refreshToken', tokens.refreshToken)
  sessionStorage.setItem('tokenType', tokens.tokenType)
  sessionStorage.setItem('accessTtl', String(tokens.accessTtl))
}

function clearStoredAuth() {
  for (const key of authStorageKeys) sessionStorage.removeItem(key)
}

const requestTokenRefresh: RefreshTokenRequest = async (refreshToken) => {
  const response = await axios.post<ApiResult<TokenVO>>('/api/user/refresh', { refreshToken }, { timeout: 5000 })
  return unwrapApiResult(response.data)
}

let activeRefresh: Promise<TokenVO> | null = null
let logoutInProgress = false

/**
 * 冻结刷新结果写回，并等待已发出的刷新完成，以便退出时撤销最新 Refresh Token。
 */
export async function beginLogout(): Promise<string | null> {
  logoutInProgress = true
  const pending = activeRefresh
  if (pending) {
    try {
      return (await pending).refreshToken
    } catch {
      // 刷新失败时仍尝试撤销浏览器中原有的 Refresh Token。
    }
  }
  return sessionStorage.getItem('refreshToken')
}

export function endLogout() {
  logoutInProgress = false
}

export function createHttpClient(
  onUnauthorized?: () => void,
  refreshTokenRequest: RefreshTokenRequest = requestTokenRefresh,
): AxiosInstance {
  const client = axios.create({ baseURL: '/api', timeout: 5000 })

  client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = sessionStorage.getItem('token')
    const tokenType = sessionStorage.getItem('tokenType') || 'Bearer'
    if (token) config.headers.set('Authorization', `${tokenType} ${token}`)
    return config
  })
  client.interceptors.response.use(
    response => unwrapApiResult(response.data as ApiResult<unknown>) as any,
    async (error: AxiosError) => {
      const status = error.response?.status
      if (status === 401) {
        if (logoutInProgress) {
          clearStoredAuth()
          throw new ApiError('正在退出登录', 401)
        }
        const request = error.config as RetryableRequestConfig | undefined
        const refreshToken = sessionStorage.getItem('refreshToken')

        if (request && !request.skipAuthReplay && !request._retry && refreshToken) {
          request._retry = true
          let currentRefresh: Promise<TokenVO> | null = null
          try {
            activeRefresh ??= refreshTokenRequest(refreshToken)
            currentRefresh = activeRefresh
            const tokens = await currentRefresh
            if (logoutInProgress) {
              clearStoredAuth()
              throw new ApiError('正在退出登录', 401)
            }
            saveTokens(tokens)
            request.headers.set('Authorization', `${tokens.tokenType || 'Bearer'} ${tokens.accessToken}`)
            return await client.request(request)
          } catch {
            clearStoredAuth()
            if (!logoutInProgress) onUnauthorized?.()
            throw new ApiError('登录状态已失效，请重新登录', 401)
          } finally {
            if (activeRefresh === currentRefresh) activeRefresh = null
          }
        }

        clearStoredAuth()
        onUnauthorized?.()
        throw new ApiError('登录状态已失效，请重新登录', 401)
      }
      if (error instanceof ApiError) throw error
      const message = error.response?.data && typeof error.response.data === 'object' && 'msg' in error.response.data
        ? String((error.response.data as { msg?: string }).msg || '请求失败')
        : (error.message || '网络异常，请稍后重试')
      throw new ApiError(message, status)
    },
  )
  return client
}

let unauthorizedHandler: (() => void) | undefined
/** Bind once after Pinia is created so 401 also clears reactive auth state. */
export function setUnauthorizedCallback(handler?: () => void) { unauthorizedHandler = handler }
/** @deprecated Use setUnauthorizedCallback; retained for callers created before Task 3 wiring. */
export const setUnauthorizedHandler = setUnauthorizedCallback
export let http: AxiosInstance = createHttpClient(() => unauthorizedHandler?.())

/** Domain APIs expose the unwrapped response body while retaining AxiosInstance's contract. */
export const dataOf = <T>(request: Promise<unknown>) => request as Promise<T>
