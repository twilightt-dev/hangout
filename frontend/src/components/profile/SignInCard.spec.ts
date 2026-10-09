import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SignInCard from './SignInCard.vue'

const { getSignStats, signToday } = vi.hoisted(() => ({ getSignStats: vi.fn(), signToday: vi.fn() }))
vi.mock('@/api/user', () => ({ getSignStats, signToday }))
const initial = { date: '2026-10-09', month: '202610', todaySigned: false, monthlyDays: 4, continuousDays: 0, previousMonthDays: 6, monthDifference: -2 }
const wrappers: ReturnType<typeof mount>[] = []
function create() { const wrapper = mount(SignInCard); wrappers.push(wrapper); return wrapper }

describe('个人中心签到', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-09T04:00:00Z'))
    getSignStats.mockReset().mockResolvedValue(initial)
    signToday.mockReset().mockResolvedValue({ date: '2026-10-09', alreadySigned: false })
  })
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.useRealTimers() })

  it('呈现本月累计、本月连续和上月全月比较', async () => {
    const wrapper = create(); await flushPromises()
    expect(wrapper.get('[data-test="monthly-days"]').text()).toContain('4')
    expect(wrapper.get('[data-test="continuous-days"]').text()).toContain('0')
    expect(wrapper.get('[data-test="month-comparison"]').text()).toContain('比上月全月少 2 天')
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeUndefined()
  })

  it('签到成功后禁用按钮并更新统计', async () => {
    getSignStats.mockResolvedValueOnce(initial).mockResolvedValue({ ...initial, todaySigned: true, monthlyDays: 5, continuousDays: 2, monthDifference: -1 })
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="sign-button"]').trigger('click'); await flushPromises()
    expect(wrapper.get('[data-test="sign-button"]').text()).toContain('今日已签到')
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-test="monthly-days"]').text()).toContain('5')
    expect(wrapper.get('[data-test="continuous-days"]').text()).toContain('2')
    expect(wrapper.text()).toContain('签到成功')
  })

  it('请求进行中不允许重复提交', async () => {
    signToday.mockReturnValue(new Promise(() => {}))
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="sign-button"]').trigger('click')
    expect(wrapper.get('[data-test="sign-button"]').text()).toContain('签到中')
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-test="sign-button"]').trigger('click')
    expect(signToday).toHaveBeenCalledTimes(1)
  })

  it('统计失败不会显示虚假的零天并支持重试', async () => {
    getSignStats.mockRejectedValueOnce(new Error('offline')).mockResolvedValue(initial)
    const wrapper = create(); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('统计加载失败')
    expect(wrapper.find('[data-test="monthly-days"]').exists()).toBe(false)
    await wrapper.get('[data-test="stats-retry"]').trigger('click'); await flushPromises()
    expect(wrapper.get('[data-test="monthly-days"]').text()).toContain('4')
  })

  it('写入已确认但刷新失败仍显示今日已签到', async () => {
    getSignStats.mockResolvedValueOnce(initial).mockRejectedValueOnce(new Error('offline'))
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="sign-button"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('签到成功')
    expect(wrapper.get('[data-test="sign-button"]').text()).toContain('今日已签到')
    expect(wrapper.get('[role="alert"]').text()).toContain('统计加载失败')
    expect(wrapper.find('[data-test="monthly-days"]').exists()).toBe(false)
  })

  it('重复签到显示已签到而不是失败', async () => {
    signToday.mockResolvedValue({ date: '2026-10-09', alreadySigned: true })
    getSignStats.mockResolvedValueOnce(initial).mockResolvedValue({ ...initial, todaySigned: true })
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="sign-button"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('今日已签到')
    expect(wrapper.text()).not.toContain('签到未确认')
  })

  it('签到写入失败可安全重试且不提前显示已签到', async () => {
    signToday.mockRejectedValueOnce(new Error('offline'))
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="sign-button"]').trigger('click'); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('签到未确认')
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeUndefined()
  })

  it('北京时间跨日后重新加载，不沿用昨日已签到按钮', async () => {
    vi.setSystemTime(new Date('2026-10-09T15:59:59Z'))
    getSignStats.mockResolvedValueOnce({ ...initial, todaySigned: true }).mockResolvedValue({ ...initial, date: '2026-10-10', todaySigned: false })
    const wrapper = create(); await flushPromises()
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeDefined()
    await vi.advanceTimersByTimeAsync(1100); await flushPromises()
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeUndefined()
  })

  it('页面回前台时刷新其他页面确认的签到状态', async () => {
    const wrapper = create(); await flushPromises()
    getSignStats.mockResolvedValue({ ...initial, todaySigned: true })
    window.dispatchEvent(new Event('focus')); await flushPromises()
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeDefined()
  })

  it('签到前发出的旧统计响应不能覆盖写入后的统计', async () => {
    let completeOld: ((value: typeof initial) => void) | undefined
    getSignStats.mockReturnValueOnce(new Promise(resolve => { completeOld = resolve }))
      .mockResolvedValue({ ...initial, todaySigned: true, monthlyDays: 5 })
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="sign-button"]').trigger('click'); await flushPromises()
    completeOld?.(initial); await flushPromises()
    expect(wrapper.get('[data-test="monthly-days"]').text()).toContain('5')
    expect(wrapper.get('[data-test="sign-button"]').attributes('disabled')).toBeDefined()
  })

  it('跨午夜期间签到请求失败仍重新加载新一天的统计', async () => {
    vi.setSystemTime(new Date('2026-10-09T15:59:59Z'))
    let failSign: ((reason: Error) => void) | undefined
    signToday.mockReturnValue(new Promise((_resolve, reject) => { failSign = reject }))
    getSignStats.mockResolvedValueOnce(initial).mockResolvedValue({ ...initial, date: '2026-10-10' })
    const wrapper = create(); await flushPromises()
    await wrapper.get('[data-test="sign-button"]').trigger('click')
    await vi.advanceTimersByTimeAsync(1100)
    failSign?.(new Error('offline')); await flushPromises()
    expect(getSignStats).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-test="monthly-days"]').text()).toContain('4')
  })

  it('卸载后清理跨日定时器和前台事件监听', async () => {
    const wrapper = create(); await flushPromises()
    wrapper.unmount()
    window.dispatchEvent(new Event('focus'))
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(86_400_000); await flushPromises()
    expect(getSignStats).toHaveBeenCalledTimes(1)
  })
})
