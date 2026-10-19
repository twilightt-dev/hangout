import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ShopSetmealSection from './ShopSetmealSection.vue'

const { getShopSetmeals } = vi.hoisted(() => ({ getShopSetmeals: vi.fn() }))
vi.mock('@/api/shopSetmeal', () => ({ getShopSetmeals }))

const meal = {
  id: 1, shopId: 8, name: '双人餐', coverImage: '/meal.png', price: 9900, originalPrice: 12900,
  people: 2, description: '一起分享好味道', rules: '无需预约\n周末可用',
  items: [{ name: '烤鱼', quantity: 1, unit: '份' }, { name: '饮料', quantity: 2, unit: '杯' }],
}
const page = (records: Array<Omit<typeof meal, 'originalPrice' | 'description'> & { originalPrice?: number | null; description?: string | null }>) => ({ records, total: records.length, current: 1, size: 10, pages: records.length ? 1 : 0 })
const wrappers: ReturnType<typeof mount>[] = []
function create(shopId = 8) {
  const wrapper = mount(ShopSetmealSection, { props: { shopId } })
  wrappers.push(wrapper)
  return wrapper
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

describe('店铺套餐展示', () => {
  beforeEach(() => { getShopSetmeals.mockReset().mockResolvedValue(page([meal])) })
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

  it('展示以元计的价格，点击卡片直接展开固定菜品与多行纯文本规则', async () => {
    const wrapper = create()
    await flushPromises()
    expect(wrapper.text()).toContain('双人餐')
    expect(wrapper.get('[data-test="setmeal-price"]').text()).toBe('¥99.00')
    const details = wrapper.get('details')
    expect(details.attributes('open')).toBeUndefined()
    await wrapper.get('summary').trigger('click')
    expect(details.attributes('open')).toBeDefined()
    expect(wrapper.get('[data-test="setmeal-items"]').text()).toContain('烤鱼 × 1 份')
    expect(wrapper.get('[data-test="setmeal-rules"]').text()).toBe('无需预约\n周末可用')
    expect(getShopSetmeals).toHaveBeenCalledTimes(1)
    expect(wrapper.find('a').exists()).toBe(false)
  })

  it('店铺没有套餐时隐藏整个区块', async () => {
    getShopSetmeals.mockResolvedValue(page([]))
    const wrapper = create()
    await flushPromises()
    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('封面加载失败显示占位，缺少选填字段时隐藏原价与简介', async () => {
    getShopSetmeals.mockResolvedValue(page([{ ...meal, originalPrice: null, description: null }]))
    const wrapper = create()
    await flushPromises()
    expect(wrapper.find('del').exists()).toBe(false)
    expect(wrapper.find('.setmeal-description').exists()).toBe(false)
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('双人餐暂无图片')
  })

  it('加载失败保留可重试区块，重试成功后展示套餐', async () => {
    getShopSetmeals.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(page([meal]))
    const wrapper = create()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('套餐加载失败')
    await wrapper.get('[data-test="setmeal-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('双人餐')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('切店时取消旧请求，迟到的旧结果不能覆盖新店套餐', async () => {
    const old = deferred<ReturnType<typeof page>>()
    getShopSetmeals.mockReturnValueOnce(old.promise).mockResolvedValue(page([{ ...meal, shopId: 9, name: '新店套餐' }]))
    const wrapper = create()
    await wrapper.setProps({ shopId: 9 })
    await flushPromises()
    expect(wrapper.text()).toContain('新店套餐')
    expect(getShopSetmeals.mock.calls[0]?.[1]?.aborted).toBe(true)
    old.resolve(page([{ ...meal, name: '旧店套餐' }]))
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧店套餐')
    wrapper.unmount()
    expect(getShopSetmeals.mock.calls[1]?.[1]?.aborted).toBe(true)
  })
})
