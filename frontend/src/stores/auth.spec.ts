import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import axios from 'axios'
import { http, setUnauthorizedCallback } from '@/api/http'

const api = vi.hoisted(() => ({
  login: vi.fn(), logout: vi.fn(), getCurrentUser: vi.fn(),
}))
vi.mock('../api/user', () => api)

describe('auth store', () => {
  beforeEach(() => { sessionStorage.clear(); setActivePinia(createPinia()); vi.clearAllMocks() })

  it('restores token and cached user, removing malformed JSON', () => {
    sessionStorage.setItem('token', 'abc'); sessionStorage.setItem('userInfo', '{bad')
    const store = useAuthStore(); store.restore()
    expect(store.token).toBe('abc'); expect(store.user).toBeNull(); expect(sessionStorage.getItem('userInfo')).toBeNull()
  })

  it('登录后保存双 Token，再查询当前用户', async () => {
    api.login.mockResolvedValue({ accessToken: 'access-jwt', refreshToken: 'refresh-jwt', tokenType: 'Bearer', accessTtl: 900 })
    api.getCurrentUser.mockResolvedValue({ id: 2, nickName: '星' })
    const store = useAuthStore()
    await store.signIn({ phone: '13812345678', loginType: 'code', verifyCode: '123456' })
    expect(store.token).toBe('access-jwt')
    expect(store.user?.id).toBe(2)
    expect(sessionStorage.getItem('token')).toBe('access-jwt')
    expect(sessionStorage.getItem('refreshToken')).toBe('refresh-jwt')
    expect(sessionStorage.getItem('tokenType')).toBe('Bearer')
    expect(sessionStorage.getItem('accessTtl')).toBe('900')
  })

  it('当前用户资料暂时加载失败时仍保留已成功签发的会话', async () => {
    api.login.mockResolvedValue({ accessToken: 'access-jwt', refreshToken: 'refresh-jwt', tokenType: 'Bearer', accessTtl: 900 })
    api.getCurrentUser.mockRejectedValue(new Error('profile unavailable'))
    const store = useAuthStore()

    await expect(store.signIn({ phone: '13812345678', loginType: 'password', password: 'Test123456' }))
      .resolves.toBeNull()
    expect(store.token).toBe('access-jwt')
    expect(store.user).toBeNull()
  })

  it('头像保存成功后同步更新当前用户和会话缓存', () => {
    const store = useAuthStore()
    store.setSession({ accessToken: 'jwt', refreshToken: 'refresh-jwt', tokenType: 'Bearer', accessTtl: 900 }, { id: 1, nickName: '星', icon: '/imgs/old.png' })

    store.updateAvatar('/imgs/new.png')

    expect(store.user?.icon).toBe('/imgs/new.png')
    expect(JSON.parse(sessionStorage.getItem('userInfo') || '{}').icon).toBe('/imgs/new.png')
  })

  it('always clears local session when logout fails', async () => {
    const store = useAuthStore()
    store.setSession({ accessToken: 'jwt', refreshToken: 'refresh-jwt', tokenType: 'Bearer', accessTtl: 900 }, { id: 1 })
    api.logout.mockRejectedValue(new Error('offline'))
    await expect(store.signOut()).rejects.toThrow('offline')
    expect(api.logout).toHaveBeenCalledWith('refresh-jwt')
    expect(store.token).toBeNull(); expect(store.user).toBeNull(); expect(sessionStorage.length).toBe(0)
  })

  it('clears the existing store memory when a bound client receives 401', async () => {
    const store = useAuthStore()
    store.setSession({ accessToken: 'jwt', refreshToken: 'refresh-jwt', tokenType: 'Bearer', accessTtl: 900 }, { id: 1 })
    sessionStorage.removeItem('refreshToken')
    setUnauthorizedCallback(store.clearSession)
    http.defaults.adapter = async (config) => {
      throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} })
    }
    await expect(http.get('/private')).rejects.toMatchObject({ code: 401 })
    expect(store.token).toBeNull(); expect(store.user).toBeNull(); expect(store.isAuthenticated).toBe(false)
  })
})
