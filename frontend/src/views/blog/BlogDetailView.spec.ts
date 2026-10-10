import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BlogDetailView from './BlogDetailView.vue'

const { getBlog, getBlogLikes, likeBlog, getShop, getFollowStatus, setFollow, getCurrentUser, sendVisit, push, authState } = vi.hoisted(() => ({
  getBlog: vi.fn(), getBlogLikes: vi.fn(), likeBlog: vi.fn(), getShop: vi.fn(), getFollowStatus: vi.fn(), setFollow: vi.fn(), getCurrentUser: vi.fn(), sendVisit: vi.fn(), push: vi.fn(), authState: { authenticated: false, user: null as null | { id: number } },
}))
const route = reactive({ params: { id: '8' as string | undefined }, fullPath: '/blogs/8' })
vi.mock('@/api/blog', () => ({ getBlog, getBlogLikes, likeBlog }))
vi.mock('@/api/shop', () => ({ getShop }))
vi.mock('@/api/follow', () => ({ getFollowStatus, setFollow }))
vi.mock('@/api/user', () => ({ getCurrentUser }))
vi.mock('@/api/traffic', async (importOriginal) => ({ ...await importOriginal<typeof import('@/api/traffic')>(), sendVisit }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ get isAuthenticated() { return authState.authenticated }, get user() { return authState.user } }) }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() } }))

describe('BlogDetailView', () => {
  const wrappers: Array<ReturnType<typeof mount>> = []
  const mountView = () => { const wrapper = mount(BlogDetailView); wrappers.push(wrapper); return wrapper }
  afterEach(() => { wrappers.splice(0).forEach((wrapper) => wrapper.unmount()) })
  beforeEach(() => {
    localStorage.clear(); sendVisit.mockReset().mockResolvedValue({ count: 12 })
    route.params.id = '8'; route.fullPath = '/blogs/8'; authState.authenticated = false; authState.user = null; push.mockReset()
    getBlog.mockReset().mockResolvedValue({ id: 8, userId: 3, shopId: 5, name: '小林', title: '午后咖啡', content: '<img src=x onerror="alert(1)">\n保留换行', images: 'https://img.example/a.jpg,https://img.example/b.jpg', liked: 3 })
    getBlogLikes.mockReset().mockResolvedValue([]); getShop.mockReset().mockResolvedValue({ id: 5, name: '巷口咖啡' }); likeBlog.mockReset().mockResolvedValue(undefined); getFollowStatus.mockReset().mockResolvedValue(false); setFollow.mockReset().mockResolvedValue(undefined); getCurrentUser.mockReset().mockResolvedValue({ id: 1 })
  })

  it('成功展示后上报博客 PV，关联店铺查询和点赞刷新不增加访问', async () => {
    authState.authenticated = true
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('累计浏览 12 次')
    expect(sendVisit).toHaveBeenCalledTimes(1)
    expect(sendVisit.mock.calls[0]?.slice(0, 2)).toEqual(['blog', 8])
    await wrapper.get('[data-test="blog-like"]').trigger('click')
    await flushPromises()
    expect(sendVisit).toHaveBeenCalledTimes(1)
  })

  it('统计拒绝时仍显示正文并显示暂无统计', async () => {
    sendVisit.mockRejectedValue(Object.assign(new Error('limited'), { code: 429 }))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('午后咖啡')
    expect(wrapper.get('[data-test="visit-statistic"]').text()).toBe('暂无统计')
    expect(sendVisit).toHaveBeenCalledTimes(1)
  })

  it('详情失败以及卸载后到达的详情都不上报', async () => {
    getBlog.mockRejectedValueOnce(new Error('network'))
    const failed = mountView()
    await flushPromises()
    expect(failed.text()).toContain('博客详情加载失败')
    expect(sendVisit).not.toHaveBeenCalled()
    let finish!: (value: { id: number; title: string }) => void
    getBlog.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const gone = mountView()
    gone.unmount()
    finish({ id: 8, title: '已离开的博客' })
    await flushPromises()
    expect(sendVisit).not.toHaveBeenCalled()
  })

  it('不存在和无效路由不上报，重新进入同一博客使用新的事件 ID', async () => {
    getBlog.mockResolvedValueOnce(null)
    const empty = mountView()
    await flushPromises()
    expect(empty.text()).toContain('博客不存在')
    expect(sendVisit).not.toHaveBeenCalled()
    empty.unmount()
    route.params.id = 'invalid'
    const invalid = mountView()
    await flushPromises()
    expect(invalid.text()).toContain('博客地址无效')
    expect(sendVisit).not.toHaveBeenCalled()
    invalid.unmount()
    route.params.id = '8'
    const shown = mountView()
    await flushPromises()
    const first = sendVisit.mock.calls[0]?.[2]
    shown.unmount()
    const reopened = mountView()
    await flushPromises()
    const second = sendVisit.mock.calls[1]?.[2]
    expect(first.visitorId).toBe(second.visitorId)
    expect(first.eventId).not.toBe(second.eventId)
    reopened.unmount()
  })

  it('统计上报在详情正文已写入 DOM 后执行', async () => {
    const wrapper = mountView()
    sendVisit.mockImplementation(() => {
      expect(wrapper.get('[data-test="blog-body"]').text()).toContain('保留换行')
      return Promise.resolve({ count: 1 })
    })
    await flushPromises()
    expect(wrapper.text()).toContain('累计浏览 1 次')
  })

  it('将博客正文作为纯文本渲染，不执行存量 HTML', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-test="blog-body"]').text()).toContain('<img src=x onerror="alert(1)">')
    expect(wrapper.find('[data-test="blog-body"] img').exists()).toBe(false)
  })

  it('未登录点赞时跳转登录并保留详情地址', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-test="blog-like"]').trigger('click')

    expect(push).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/blogs/8' } })
    expect(likeBlog).not.toHaveBeenCalled()
  })

  it('点赞成功后刷新博客与点赞用户列表', async () => {
    authState.authenticated = true
    getBlog.mockResolvedValueOnce({ id: 8, userId: 3, shopId: 5, title: '旧标题', content: '正文', liked: 3 }).mockResolvedValueOnce({ id: 8, userId: 3, shopId: 5, title: '新标题', content: '正文', liked: 4, isLike: true })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-test="blog-like"]').trigger('click')
    await flushPromises()

    expect(likeBlog).toHaveBeenCalledWith(8)
    expect(getBlog).toHaveBeenCalledTimes(2)
    expect(getBlogLikes).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('新标题')
    expect(wrapper.text()).toContain('4')
  })

  it('认证用户身份读取失败时不把博客误判为可关注的他人', async () => {
    authState.authenticated = true
    getCurrentUser.mockRejectedValue(new Error('network'))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.follow-button').exists()).toBe(false)
    expect(setFollow).not.toHaveBeenCalled()
  })

  it('博客响应没有有效数值 ID 时不请求点赞用户', async () => {
    getBlog.mockResolvedValue({ userId: 3, shopId: 5, title: '无 ID 博客', content: '正文' })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('无 ID 博客')
    expect(getBlogLikes).not.toHaveBeenCalled()
  })

  it('快速切换或切至无效路由时旧详情响应不会覆盖当前页面', async () => {
    let resolveOld!: (value: { id: number; title: string; content: string }) => void
    const oldRequest = new Promise<{ id: number; title: string; content: string }>((resolve) => { resolveOld = resolve })
    getBlog.mockImplementationOnce(() => oldRequest).mockResolvedValueOnce({ id: 9, title: '新博客', content: '新正文' })
    const wrapper = mountView()
    await nextTick()
    route.params.id = '9'; route.fullPath = '/blogs/9'
    await nextTick(); await flushPromises()
    resolveOld({ id: 8, title: '旧博客', content: '旧正文' })
    await flushPromises()

    expect(wrapper.text()).toContain('新博客')
    expect(wrapper.text()).not.toContain('旧博客')
    route.params.id = undefined; route.fullPath = '/blogs/'
    await nextTick(); await flushPromises()
    expect(wrapper.text()).toContain('博客地址无效')
    expect(wrapper.text()).not.toContain('新博客')
  })

  it('切换路由后旧点赞请求不会刷新或锁定新博客', async () => {
    let finishLike!: () => void
    const pendingLike = new Promise<void>((resolve) => { finishLike = resolve })
    authState.authenticated = true
    likeBlog.mockReturnValue(pendingLike)
    getBlog.mockImplementation((id: number) => Promise.resolve({ id, userId: id + 10, title: `博客${id}`, content: '正文', liked: 0 }))
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-test="blog-like"]').trigger('click')
    route.params.id = '9'; route.fullPath = '/blogs/9'
    await nextTick(); await flushPromises()
    finishLike()
    await flushPromises()

    expect(wrapper.text()).toContain('博客9')
    expect(wrapper.get('[data-test="blog-like"]').attributes('disabled')).toBeUndefined()
    expect(getBlog).toHaveBeenCalledTimes(2)
  })
})
