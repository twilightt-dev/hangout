import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MyProfileView from './MyProfileView.vue'

const { fetchCurrentUser, signOut, getUserInfo, getMyBlogs, getFollowFeed, push, confirm } = vi.hoisted(() => ({
  fetchCurrentUser: vi.fn(), signOut: vi.fn(), getUserInfo: vi.fn(), getMyBlogs: vi.fn(), getFollowFeed: vi.fn(), push: vi.fn(), confirm: vi.fn(),
}))
vi.stubGlobal('confirm', confirm)
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ fetchCurrentUser, signOut }) }))
vi.mock('@/api/user', () => ({ getUserInfo }))
vi.mock('@/api/blog', () => ({ getMyBlogs, getFollowFeed }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

describe('MyProfileView', () => {
  afterEach(() => { vi.restoreAllMocks() })
  beforeEach(() => {
    fetchCurrentUser.mockReset().mockResolvedValue({ id: 1, nickName: '我自己' })
    getUserInfo.mockReset().mockResolvedValue({ city: '杭州', introduce: '城市漫游' })
    getMyBlogs.mockReset().mockResolvedValue([{ id: 8, title: '我的第一篇', name: '我自己' }])
    getFollowFeed.mockReset().mockResolvedValue({ list: [], minTime: 0, offset: 0 })
    signOut.mockReset(); push.mockReset(); confirm.mockReset().mockReturnValue(true)
  })

  it('呈现当前用户的真实博客列表', async () => {
    const wrapper = mount(MyProfileView)
    await flushPromises()

    expect(getMyBlogs).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('我的第一篇')
  })

  it('退出接口失败后仍由 auth 清理会话并回到首页', async () => {
    signOut.mockRejectedValue(new Error('network'))
    const wrapper = mount(MyProfileView)
    await flushPromises()
    await wrapper.get('[data-test="logout"]').trigger('click')
    await flushPromises()

    expect(signOut).toHaveBeenCalledTimes(1)
    expect(push).toHaveBeenCalledWith({ name: 'home' })
  })

  it('沿用服务端同分游标，滚动时去重，空页显示到底', async () => {
    getFollowFeed.mockResolvedValueOnce({ list: [{ id: 9, title: '第一篇' }], minTime: 1000, offset: 1 })
      .mockResolvedValueOnce({ list: [{ id: 9, title: '第一篇' }, { id: 8, title: '第二篇' }], minTime: 1000, offset: 3 })
      .mockResolvedValue({ list: [], minTime: 1000, offset: 3 })
    const wrapper = mount(MyProfileView)
    await flushPromises()
    await wrapper.get('#feed-tab').trigger('click')
    await flushPromises()
    await wrapper.get('.feed-list').trigger('scroll')
    await flushPromises()
    expect(getFollowFeed).toHaveBeenNthCalledWith(2, 1000, 1)
    expect(wrapper.findAll('.blog-card')).toHaveLength(2)
    await wrapper.get('.feed-list').trigger('scroll')
    await flushPromises()
    expect(wrapper.text()).toContain('已经到底啦~')
    await wrapper.get('.feed-list').trigger('scroll')
    expect(getFollowFeed).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('续页失败保留已加载博客及游标，点击重试继续原页', async () => {
    getFollowFeed.mockResolvedValueOnce({ list: [{ id: 9, title: '已经加载的博客' }], minTime: 1000, offset: 1 })
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ list: [], minTime: 1000, offset: 1 })
    const wrapper = mount(MyProfileView)
    await flushPromises()
    await wrapper.get('#feed-tab').trigger('click'); await flushPromises()
    await wrapper.get('.feed-list').trigger('scroll'); await flushPromises()
    expect(wrapper.text()).toContain('已经加载的博客')
    expect(wrapper.text()).toContain('关注动态加载失败')
    expect(wrapper.text()).not.toContain('已经到底啦~')
    await wrapper.get('.state-retry').trigger('click'); await flushPromises()
    expect(getFollowFeed).toHaveBeenNthCalledWith(3, 1000, 1)
    expect(wrapper.text()).toContain('已经到底啦~')
    wrapper.unmount()
  })

  it('首屏未撑满自动续页，刷新重置游标和内容', async () => {
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(400)
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(200)
    getFollowFeed.mockResolvedValueOnce({ list: [{ id: 9, title: '旧动态' }], minTime: 1000, offset: 1 })
      .mockResolvedValueOnce({ list: [], minTime: 1000, offset: 1 })
      .mockResolvedValue({ list: [], minTime: 2000, offset: 0 })
    const wrapper = mount(MyProfileView)
    await flushPromises()
    await wrapper.get('#feed-tab').trigger('click'); await flushPromises()
    expect(getFollowFeed).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('已经到底啦~')
    await wrapper.get('.feed-toolbar button').trigger('click'); await flushPromises()
    expect(getFollowFeed).toHaveBeenNthCalledWith(3, expect.any(Number), 0)
    expect(wrapper.text()).not.toContain('旧动态')
    expect(wrapper.text()).toContain('暂无关注动态')
    wrapper.unmount()
  })

  it('首屏失败可以重试，畸形成功响应不能被当作到底', async () => {
    getFollowFeed.mockResolvedValueOnce({ list: [] })
      .mockResolvedValueOnce({ list: [], minTime: 1000, offset: 0 })
    const wrapper = mount(MyProfileView)
    await flushPromises()
    await wrapper.get('#feed-tab').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('关注动态加载失败')
    expect(wrapper.text()).not.toContain('已经到底啦~')
    await wrapper.get('.state-retry').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('已经到底啦~')
    wrapper.unmount()
  })
})
