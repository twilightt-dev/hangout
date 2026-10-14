import { describe, expect, it, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { ApiError, beginLogout, createHttpClient, endLogout, unwrapApiResult } from './http'
import type { TokenVO } from '@/types/api'

describe('http client', () => {
  beforeEach(() => sessionStorage.clear())

  it('unwraps successful ApiResult and rejects failed result', () => {
    expect(unwrapApiResult({ code: 1, data: { ok: 1 } })).toEqual({ ok: 1 })
    expect(() => unwrapApiResult({ code: 0, msg: '失败' })).toThrow('失败')
  })

  it('reads token for every request and adds bearer header', async () => {
    const client = createHttpClient()
    const seen: string[] = []
    client.defaults.adapter = async (config) => {
      seen.push(String(config.headers?.Authorization ?? ''))
      return { data: { code: 1, data: 'ok' }, status: 200, statusText: 'OK', headers: {}, config }
    }
    sessionStorage.setItem('token', 'first')
    await client.get('/a')
    sessionStorage.setItem('token', 'second')
    await client.get('/b')
    expect(seen).toEqual(['Bearer first', 'Bearer second'])
  })

  it('没有 Refresh Token 时，401 清理会话并通知应用', async () => {
    const unauthorized = vi.fn()
    const client = createHttpClient(unauthorized)
    client.defaults.adapter = async (config) => {
      throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
    }
    sessionStorage.setItem('token', 't'); sessionStorage.setItem('userInfo', '{}')
    await expect(client.get('/private')).rejects.toMatchObject({ message: '登录状态已失效，请重新登录', code: 401 })
    expect(sessionStorage.getItem('token')).toBeNull()
    expect(sessionStorage.getItem('userInfo')).toBeNull()
    expect(unauthorized).toHaveBeenCalledOnce()
  })

  it('收到 401 后刷新双 Token，并携带新 Access Token 重放原请求', async () => {
    const refreshed: TokenVO = { accessToken: 'new-access', refreshToken: 'new-refresh', tokenType: 'Bearer', accessTtl: 900 }
    const refresh = vi.fn().mockResolvedValue(refreshed)
    const client = createHttpClient(undefined, refresh)
    const authorizations: string[] = []
    let attempts = 0
    client.defaults.adapter = async (config) => {
      authorizations.push(String(config.headers?.Authorization ?? ''))
      attempts += 1
      if (attempts === 1) {
        throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
      }
      return { data: { code: 1, data: 'ok' }, status: 200, statusText: 'OK', headers: {}, config }
    }
    sessionStorage.setItem('token', 'old-access')
    sessionStorage.setItem('refreshToken', 'old-refresh')

    await expect(client.get('/private')).resolves.toBe('ok')
    expect(refresh).toHaveBeenCalledWith('old-refresh')
    expect(authorizations).toEqual(['Bearer old-access', 'Bearer new-access'])
    expect(sessionStorage.getItem('token')).toBe('new-access')
    expect(sessionStorage.getItem('refreshToken')).toBe('new-refresh')
  })

  it('并发 401 只发起一次刷新请求', async () => {
    let resolveRefresh!: (tokens: TokenVO) => void
    const refresh = vi.fn(() => new Promise<TokenVO>((resolve) => { resolveRefresh = resolve }))
    const client = createHttpClient(undefined, refresh)
    client.defaults.adapter = async (config) => {
      if (config.headers?.Authorization === 'Bearer old-access') {
        throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
      }
      return { data: { code: 1, data: config.url }, status: 200, statusText: 'OK', headers: {}, config }
    }
    sessionStorage.setItem('token', 'old-access')
    sessionStorage.setItem('refreshToken', 'old-refresh')
    const requests = Promise.all([client.get('/one'), client.get('/two')])
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())
    resolveRefresh({ accessToken: 'new-access', refreshToken: 'new-refresh', tokenType: 'Bearer', accessTtl: 900 })
    await expect(requests).resolves.toEqual(['/one', '/two'])
  })

  it('评论切换遇到401也不自动重放POST', async () => {
    const refresh = vi.fn(); const client = createHttpClient(undefined, refresh); let attempts = 0
    client.defaults.adapter = async config => {
      attempts++
      throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
    }
    sessionStorage.setItem('token', '旧token'); sessionStorage.setItem('refreshToken', '刷新token')
    await expect(client.post('/blog/8/comments/21/like', undefined, { skipAuthReplay: true })).rejects.toMatchObject({ code: 401 })
    expect(attempts).toBe(1); expect(refresh).not.toHaveBeenCalled()
  })

  it('刷新失败时清理全部认证数据且不重复重试', async () => {
    const unauthorized = vi.fn()
    const refresh = vi.fn().mockRejectedValue(new Error('refresh expired'))
    const client = createHttpClient(unauthorized, refresh)
    client.defaults.adapter = async (config) => {
      throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
    }
    sessionStorage.setItem('token', 'old-access')
    sessionStorage.setItem('refreshToken', 'old-refresh')
    sessionStorage.setItem('tokenType', 'Bearer')
    sessionStorage.setItem('accessTtl', '900')
    sessionStorage.setItem('userInfo', '{}')

    await expect(client.get('/private')).rejects.toMatchObject({ code: 401 })
    expect(refresh).toHaveBeenCalledOnce()
    expect(unauthorized).toHaveBeenCalledOnce()
    expect(sessionStorage.length).toBe(0)
  })

  it('退出会等待进行中的刷新、返回最新 Refresh Token，且不允许新会话写回', async () => {
    let resolveRefresh!: (tokens: TokenVO) => void
    const refresh = vi.fn(() => new Promise<TokenVO>((resolve) => { resolveRefresh = resolve }))
    const client = createHttpClient(undefined, refresh)
    client.defaults.adapter = async (config) => {
      throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
    }
    sessionStorage.setItem('token', 'old-access')
    sessionStorage.setItem('refreshToken', 'old-refresh')
    const pendingRequest = client.get('/private')
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())

    const tokenForLogout = beginLogout()
    resolveRefresh({ accessToken: 'new-access', refreshToken: 'new-refresh', tokenType: 'Bearer', accessTtl: 900 })

    await expect(tokenForLogout).resolves.toBe('new-refresh')
    await expect(pendingRequest).rejects.toMatchObject({ code: 401 })
    expect(sessionStorage.getItem('token')).not.toBe('new-access')
    endLogout()
  })

  it('退出已经开始后，迟到的 401 不再启动新刷新', async () => {
    const refresh = vi.fn().mockResolvedValue({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
      tokenType: 'Bearer',
      accessTtl: 900,
    })
    const client = createHttpClient(undefined, refresh)
    client.defaults.adapter = async (config) => {
      throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
    }
    sessionStorage.setItem('token', 'old-access')
    sessionStorage.setItem('refreshToken', 'old-refresh')

    await expect(beginLogout()).resolves.toBe('old-refresh')
    await expect(client.get('/late-private-request')).rejects.toMatchObject({ code: 401 })

    expect(refresh).not.toHaveBeenCalled()
    endLogout()
  })

  it('uses the unified msg field for non-401 HTTP errors', async () => {
    const client = createHttpClient()
    client.defaults.adapter = async (config) => {
      throw new axios.AxiosError('Bad Request', 'ERR_BAD_REQUEST', config, undefined, {
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config,
        data: { code: 0, msg: '参数错误' },
      })
    }

    await expect(client.get('/bad')).rejects.toMatchObject({ message: '参数错误', code: 400 })
  })
})
