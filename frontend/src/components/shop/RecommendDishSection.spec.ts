import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RecommendDishSection from './RecommendDishSection.vue'

const api = vi.hoisted(() => ({ list: vi.fn(), publish: vi.fn(), recommend: vi.fn(), upload: vi.fn(), push: vi.fn() }))
vi.mock('@/api/recommendDish', () => ({ getRecommendDishes: api.list, publishRecommendDish: api.publish, recommendDish: api.recommend }))
vi.mock('@/api/upload', () => ({ uploadBlogImage: api.upload, normalizeBlogImageUrl: (path: string) => `/imgs${path}` }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }) }))
const auth = reactive({ isAuthenticated: true, user: { id: 7 } as { id: number } | null })
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

const dish = { id: 1, shopId: 8, name: '招牌烤鱼', coverImage: '/imgs/fish.png', recommendCount: 1, recommended: false, createTime: '2026-10-18T12:00:00' }
const page = (records = [dish], total = records.length, size = 6, current = 1) => ({ records, total, size, current, pages: Math.ceil(total / size) })
const wrappers: ReturnType<typeof mount>[] = []
function create() { const wrapper = mount(RecommendDishSection, { props: { shopId: 8 } }); wrappers.push(wrapper); return wrapper }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }
async function upload(wrapper: ReturnType<typeof mount>) {
  const input = wrapper.get('[data-test="dish-file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [new File(['image'], 'dish.png', { type: 'image/png' })] })
  await input.trigger('change'); await flushPromises()
}

describe('推荐菜独立业务', () => {
  beforeEach(() => {
    auth.isAuthenticated = true; auth.user = { id: 7 }
    api.list.mockReset().mockResolvedValue(page())
    api.publish.mockReset().mockResolvedValue({ ...dish, recommended: true })
    api.recommend.mockReset().mockResolvedValue({ ...dish, recommendCount: 2, recommended: true })
    api.upload.mockReset().mockResolvedValue('/blogs/a/b/photo.png'); api.push.mockReset()
  })
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

  it('展示菜品与推荐人数，游客操作引导登录', async () => {
    auth.isAuthenticated = false; auth.user = null
    const wrapper = create(); await flushPromises()
    expect(wrapper.text()).toContain('招牌烤鱼'); expect(wrapper.text()).toContain('1 人推荐')
    await wrapper.get('[data-test="recommend-1"]').trigger('click')
    expect(api.push).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/shops/8' } })
    expect(api.recommend).not.toHaveBeenCalled()
  })

  it('首次取六道，查看更多使用独立分页并能返回预览', async () => {
    api.list.mockResolvedValueOnce(page([dish], 13)).mockResolvedValueOnce(page([dish], 13, 12))
    const wrapper = create(); await flushPromises()
    expect(api.list.mock.calls[0]?.slice(0, 3)).toEqual([8, 1, 6])
    await wrapper.get('[data-test="dish-more"]').trigger('click'); await flushPromises()
    expect(api.list.mock.calls[1]?.slice(0, 3)).toEqual([8, 1, 12])
    expect(wrapper.text()).toContain('1 / 2')
    await wrapper.get('[data-test="dish-collapse"]').trigger('click'); await flushPromises()
    expect(api.list.mock.calls.at(-1)?.slice(0, 3)).toEqual([8, 1, 6])
  })

  it('查看更多或翻页失败时不把旧列表显示成新页，重试加载目标页', async () => {
    api.list.mockResolvedValueOnce(page([dish], 13)).mockRejectedValueOnce(new Error('offline'))
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="dish-more"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).not.toContain('招牌烤鱼')
    expect(wrapper.find('[data-test="dish-next"]').exists()).toBe(false)
    api.list.mockResolvedValueOnce(page([dish], 13, 12))
    await wrapper.get('[data-test="dish-retry"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('1 / 2')
    api.list.mockRejectedValueOnce(new Error('offline'))
    await wrapper.get('[data-test="dish-next"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).not.toContain('招牌烤鱼')
    api.list.mockResolvedValueOnce(page([{ ...dish, id: 2, name: '第二页菜品' }], 13, 12, 2))
    await wrapper.get('[data-test="dish-retry"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('第二页菜品'); expect(wrapper.text()).toContain('2 / 2')
  })

  it('推荐期间禁止连点，成功采用服务端人数并显示已推荐', async () => {
    const pending = deferred<typeof dish>(); api.recommend.mockReturnValueOnce(pending.promise)
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="recommend-1"]').trigger('click')
    expect(wrapper.get('[data-test="recommend-1"]').attributes('disabled')).toBeDefined()
    api.list.mockResolvedValue(page([{ ...dish, recommendCount: 2, recommended: true }]))
    pending.resolve({ ...dish, recommendCount: 2, recommended: true }); await flushPromises()
    expect(wrapper.text()).toContain('2 人推荐')
    expect(wrapper.get('[data-test="recommend-1"]').text()).toBe('已推荐')
    expect(api.recommend).toHaveBeenCalledTimes(1)
  })

  it('查询失败展示重试而不是暂无菜品', async () => {
    api.list.mockRejectedValueOnce(new Error('offline'))
    const wrapper = create(); await flushPromises()
    expect(wrapper.get('[data-test="dish-error"]').text()).toContain('加载失败')
    expect(wrapper.text()).not.toContain('暂无推荐菜')
    await wrapper.get('[data-test="dish-retry"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('招牌烤鱼')
  })

  it('上传失败不提交菜品，上传成功后才允许发布', async () => {
    api.upload.mockRejectedValueOnce(new Error('offline'))
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="dish-publish-open"]').trigger('click')
    await wrapper.get('[data-test="dish-name"]').setValue('新菜品')
    await upload(wrapper)
    expect(wrapper.text()).toContain('图片上传失败')
    expect(wrapper.get('[data-test="dish-submit"]').attributes('disabled')).toBeDefined()
    expect(api.publish).not.toHaveBeenCalled()
    await upload(wrapper)
    expect(wrapper.get('[data-test="dish-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('发布结果不明时保留表单和提交标识，重试成功后清空', async () => {
    api.publish.mockRejectedValueOnce(new Error('网络中断'))
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="dish-publish-open"]').trigger('click')
    await wrapper.get('[data-test="dish-name"]').setValue('新菜品'); await upload(wrapper)
    await wrapper.get('form').trigger('submit'); await flushPromises()
    const first = api.publish.mock.calls[0]?.[1]
    expect((wrapper.get('[data-test="dish-name"]').element as HTMLInputElement).value).toBe('新菜品')
    await wrapper.get('form').trigger('submit'); await flushPromises()
    expect(api.publish.mock.calls[1]?.[1]).toEqual(first)
    expect(first).toMatchObject({ name: '新菜品', coverImage: '/imgs/blogs/a/b/photo.png' })
    expect(first.requestId).toMatch(/^[0-9a-f-]{36}$/)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('菜品已发布')
  })

  it('切店后忽略迟到的旧列表，退出登录后刷新推荐状态', async () => {
    const old = deferred<ReturnType<typeof page>>(); api.list.mockReturnValueOnce(old.promise)
    const wrapper = create()
    api.list.mockResolvedValue(page([{ ...dish, shopId: 9, name: '新店菜品' }]))
    await wrapper.setProps({ shopId: 9 }); await flushPromises()
    old.resolve(page()); await flushPromises()
    expect(wrapper.text()).toContain('新店菜品'); expect(wrapper.text()).not.toContain('招牌烤鱼')
    auth.isAuthenticated = false; auth.user = null; await flushPromises()
    expect(api.list.mock.calls.at(-1)?.[0]).toBe(9)
  })
})
