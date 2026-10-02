import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserProfileView from './UserProfileView.vue'

const { getUser, getUserInfo, getBlogsByUser, getFollowStatus, getCommonFollows, setFollow, fetchCurrentUser, push, replace, authState } = vi.hoisted(() => ({
  getUser: vi.fn(), getUserInfo: vi.fn(), getBlogsByUser: vi.fn(), getFollowStatus: vi.fn(), getCommonFollows: vi.fn(), setFollow: vi.fn(), fetchCurrentUser: vi.fn(), push: vi.fn(), replace: vi.fn(), authState: { authenticated: false, user: null as null | { id: number } },
}))
const route = reactive({ params: { id: '1' as string | undefined }, fullPath: '/users/1' })
vi.mock('@/api/user', () => ({ getUser, getUserInfo }))
vi.mock('@/api/blog', () => ({ getBlogsByUser }))
vi.mock('@/api/follow', () => ({ getFollowStatus, getCommonFollows, setFollow }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ get isAuthenticated() { return authState.authenticated }, get user() { return authState.user }, fetchCurrentUser }) }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push, replace }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done }); return { promise, resolve } }

describe('UserProfileView', () => {
  const wrappers: Array<ReturnType<typeof mount>> = []
  const mountView = () => { const wrapper = mount(UserProfileView); wrappers.push(wrapper); return wrapper }
  afterEach(() => { wrappers.splice(0).forEach((wrapper) => wrapper.unmount()) })
  beforeEach(() => {
    route.params.id = '1'; route.fullPath = '/users/1'; authState.authenticated = false; authState.user = null
    getUser.mockReset().mockResolvedValue({ id: 1, nickName: '旧用户' }); getUserInfo.mockReset().mockResolvedValue({ city: '杭州' }); getBlogsByUser.mockReset().mockResolvedValue([]); getFollowStatus.mockReset().mockResolvedValue(false); getCommonFollows.mockReset().mockResolvedValue([]); setFollow.mockReset(); fetchCurrentUser.mockReset(); push.mockReset(); replace.mockReset()
  })

  it('快速切换用户路由后忽略旧详情响应，并在无效 ID 时清空旧内容', async () => {
    const first = deferred<{ id: number; nickName: string }>()
    getUser.mockImplementationOnce(() => first.promise).mockResolvedValueOnce({ id: 2, nickName: '新用户' })
    const wrapper = mountView()
    await nextTick()
    route.params.id = '2'; route.fullPath = '/users/2'
    await nextTick(); await flushPromises()
    first.resolve({ id: 1, nickName: '旧用户' })
    await flushPromises()

    expect(wrapper.text()).toContain('新用户')
    expect(wrapper.text()).not.toContain('旧用户')
    route.params.id = undefined; route.fullPath = '/users/'
    await nextTick(); await flushPromises()
    expect(wrapper.text()).toContain('用户地址无效')
    expect(wrapper.text()).not.toContain('新用户')
  })

  it('切换路由后不让旧共同关注响应写入当前用户页面', async () => {
    const common = deferred<Array<{ id: number; nickName: string }>>()
    getCommonFollows.mockReturnValue(common.promise)
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[role="tab"][aria-selected="false"]').trigger('click')
    route.params.id = '2'; route.fullPath = '/users/2'
    await nextTick(); await flushPromises()
    common.resolve([{ id: 99, nickName: '旧共同关注' }])
    await flushPromises()

    expect(wrapper.text()).not.toContain('旧共同关注')
  })

  it('认证但身份缺失时先恢复当前用户，发现目标是自己则跳转我的主页', async () => {
    authState.authenticated = true; authState.user = null; fetchCurrentUser.mockResolvedValue({ id: 1, nickName: '我自己' })
    const wrapper = mountView()
    await flushPromises()

    expect(fetchCurrentUser).toHaveBeenCalledTimes(1)
    expect(replace).toHaveBeenCalledWith({ name: 'me' })
    expect(getUser).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="toggle-follow"]').exists()).toBe(false)
  })

  it('身份恢复失败时仍加载公开资料但不展示关注入口', async () => {
    authState.authenticated = true; fetchCurrentUser.mockRejectedValue(new Error('network'))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('旧用户')
    expect(wrapper.find('[data-test="toggle-follow"]').exists()).toBe(false)
    expect(setFollow).not.toHaveBeenCalled()
  })

  it('切换路由后旧关注请求成功不会改写新用户的关注状态或 busy 状态', async () => {
    const followRequest = deferred<void>()
    authState.authenticated = true; authState.user = { id: 99 }
    getUser.mockImplementation((id: number) => Promise.resolve({ id, nickName: `用户${id}` }))
    setFollow.mockReturnValue(followRequest.promise)
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-test="toggle-follow"]').trigger('click')
    expect(setFollow).toHaveBeenCalledWith(1, true)
    route.params.id = '2'; route.fullPath = '/users/2'
    await nextTick(); await flushPromises()
    followRequest.resolve()
    await flushPromises()

    const follow = wrapper.get('[data-test="toggle-follow"]')
    expect(follow.attributes('aria-pressed')).toBe('false')
    expect(follow.attributes('disabled')).toBeUndefined()
  })
})
