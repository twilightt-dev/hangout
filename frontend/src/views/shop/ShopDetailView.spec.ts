import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ShopDetailView from './ShopDetailView.vue'
import { ApiError } from '@/api/http'

const { getShop, getVouchers, seckillVoucher, push, messageError, authState } = vi.hoisted(() => ({
  getShop: vi.fn(), getVouchers: vi.fn(), seckillVoucher: vi.fn(), push: vi.fn(), messageError: vi.fn(), authState: { authenticated: false },
}))

const route = reactive({ params: { id: '8' }, fullPath: '/shops/8' })
vi.mock('@/api/shop', () => ({ getShop, getVouchers, seckillVoucher }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: messageError, success: vi.fn(), warning: vi.fn() } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ get isAuthenticated() { return authState.authenticated } }) }))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

describe('ShopDetailView', () => {
  beforeEach(() => {
    route.params.id = '8'; route.fullPath = '/shops/8'
    getShop.mockReset().mockResolvedValue({ id: 8, name: '巷口咖啡' })
    getVouchers.mockReset().mockResolvedValue([])
    seckillVoucher.mockReset().mockResolvedValue(99)
    push.mockReset(); messageError.mockReset(); authState.authenticated = false
  })

  it('未登录领取优惠券时跳转登录并保留当前地址', async () => {
    const wrapper = mount(ShopDetailView)
    await flushPromises()
    await wrapper.vm.claim({ id: 5, stock: 2, beginTime: '2026-09-01 00:00:00', endTime: '2026-10-01 00:00:00' })

    expect(push).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/shops/8' } })
    expect(seckillVoucher).not.toHaveBeenCalled()
  })

  it('过期或库存不足的优惠券不调用领取接口', async () => {
    authState.authenticated = true
    const wrapper = mount(ShopDetailView)
    await flushPromises()
    await wrapper.vm.claim({ id: 5, stock: 2, beginTime: '2020-01-01 00:00:00', endTime: '2020-01-02 00:00:00' })
    await wrapper.vm.claim({ id: 6, stock: 0, beginTime: '2026-01-01 00:00:00', endTime: '2099-01-01 00:00:00' })

    expect(seckillVoucher).not.toHaveBeenCalled()
    expect(messageError).toHaveBeenCalledTimes(2)
  })

  it('将后端放大十倍保存的评分按五分制展示', async () => {
    getShop.mockResolvedValue({ id: 8, name: '巷口咖啡', score: 48 })
    const wrapper = mount(ShopDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('★ 4.8')
  })

  it('数值门店 ID 返回空数据时显示可重试错误状态', async () => {
    getShop.mockResolvedValue(null)
    const wrapper = mount(ShopDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('门店不存在或已下线，请稍后重试。')
    expect(wrapper.find('.state-retry').exists()).toBe(true)
  })

  it('复用详情组件时随路由门店 ID 重新加载', async () => {
    const wrapper = mount(ShopDetailView)
    await flushPromises()
    route.params.id = '9'
    route.fullPath = '/shops/9'
    await nextTick()
    await flushPromises()

    expect(getShop).toHaveBeenLastCalledWith(9)
    expect(getVouchers).toHaveBeenLastCalledWith(9)
    expect(wrapper.text()).toContain('巷口咖啡')
  })

  it('门店 ID 切换时忽略慢到的旧详情响应', async () => {
    const oldRequest = deferred<{ id: number; name: string }>()
    const newRequest = deferred<{ id: number; name: string }>()
    getShop.mockImplementation((id: number) => id === 8 ? oldRequest.promise : newRequest.promise)
    const wrapper = mount(ShopDetailView)
    await nextTick()

    route.params.id = '9'
    await nextTick()
    newRequest.resolve({ id: 9, name: '新门店' })
    await flushPromises()
    oldRequest.resolve({ id: 8, name: '旧门店' })
    await flushPromises()

    expect(wrapper.text()).toContain('新门店')
    expect(wrapper.text()).not.toContain('旧门店')
  })

  it('从有效门店切换到无效 ID 时旧响应不能恢复详情', async () => {
    const oldRequest = deferred<{ id: number; name: string }>()
    getShop.mockImplementationOnce(() => oldRequest.promise)
    const wrapper = mount(ShopDetailView)
    await nextTick()

    route.params.id = 'invalid'
    await nextTick()
    oldRequest.resolve({ id: 8, name: '旧门店' })
    await flushPromises()

    expect(wrapper.text()).toContain('门店地址无效，请返回后重新选择。')
    expect(wrapper.text()).not.toContain('旧门店')
  })

  it.each(['活动未开始！', '活动已结束！', '优惠券不存在！', '库存不足！', '不允许重复下单', '用户已经购买过一次！'])(
    '领取失败时弹出后端原始信息：%s，并恢复按钮', async (message) => {
      authState.authenticated = true
      getVouchers.mockResolvedValue([{ id: 17, title: '限时券', type: 1, stock: 1 }])
      seckillVoucher.mockRejectedValueOnce(new ApiError(message, 0))
      const wrapper = mount(ShopDetailView)
      await flushPromises()
      await wrapper.get('.voucher-card__claim').trigger('click')
      await flushPromises()

      expect(messageError).toHaveBeenCalledExactlyOnceWith(message)
      expect(wrapper.get('.voucher-card__claim').attributes('disabled')).toBeUndefined()
      expect(wrapper.get('.voucher-card__claim').text()).toBe('立即秒杀')
      wrapper.unmount()
    },
  )

  it('未知异常仍有兜底提示', async () => {
    authState.authenticated = true
    seckillVoucher.mockRejectedValueOnce(null)
    const wrapper = mount(ShopDetailView)
    await flushPromises()
    await wrapper.vm.claim({ id: 17, stock: 1 })
    expect(messageError).toHaveBeenCalledExactlyOnceWith('领取未成功，请稍后重试')
    wrapper.unmount()
  })

  it('点击真实优惠券卡片会进入领取接口', async () => {
    authState.authenticated = true
    getVouchers.mockResolvedValue([{ id: 17, title: '限时券', type: 1, stock: 1 }])
    const wrapper = mount(ShopDetailView)
    await flushPromises()
    await wrapper.get('.voucher-card__claim').trigger('click')

    expect(seckillVoucher).toHaveBeenCalledWith(17)
  })
})
