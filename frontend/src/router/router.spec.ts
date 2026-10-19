import { describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter, routes } from './index'

describe('应用路由契约', () => {
  it('包含完整命名路由表与保护元数据', () => {
    const expected = ['shop-section-list', 'home', 'login', 'password-login', 'register', 'shops', 'shop-detail', 'blog-detail', 'publish', 'me', 'edit-profile', 'user-profile', 'not-found']
    expect(routes.map((route) => route.name)).toEqual(expected)
    expect(routes.filter((route) => route.meta?.requiresAuth).map((route) => route.name)).toEqual(['publish', 'me', 'edit-profile'])
  })

  it('未认证访问保护路由时重定向登录并保留目标地址', async () => {
    const router = createAppRouter(createMemoryHistory(), () => null)
    await router.push('/publish')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/publish')
  })

  it('认证后允许访问保护路由，且动态路由可解析', async () => {
    const router = createAppRouter(createMemoryHistory(), () => 'token')
    await router.push('/me/edit')
    expect(router.currentRoute.value.name).toBe('edit-profile')
    await router.push('/shops/42')
    expect(router.currentRoute.value.name).toBe('shop-detail')
  })
})
