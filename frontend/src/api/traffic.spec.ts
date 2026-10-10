import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getVisitorId, reportVisit, sendVisit } from './traffic'
import { http } from './http'

describe('traffic visit service', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })

  it('博客与店铺上报使用资源路径并传递取消信号', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ count: 8 })
    const payload = { visitorId: '12742b76-c3d1-4586-b97a-a7835031c062', eventId: '87c4a540-f39b-4821-a0e9-5b90880a95ad' }
    const config = { signal: new AbortController().signal }
    expect(await sendVisit('blog', 5, payload, config)).toEqual({ count: 8 })
    expect(post).toHaveBeenLastCalledWith('/blog/5/visit', payload, config)
    await sendVisit('shop', 9, payload, config)
    expect(post).toHaveBeenLastCalledWith('/shop/9/visit', payload, config)
  })

  it.each([400, 404, 429])('HTTP %s 不重试', async code => {
    const request = vi.fn().mockRejectedValue(Object.assign(new Error('rejected'), { code }))
    await expect(reportVisit(request)).rejects.toThrow('rejected')
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('网络与服务故障只按 500ms 和 1500ms 退避重试三次', async () => {
    vi.useFakeTimers()
    const request = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockRejectedValueOnce(Object.assign(new Error('server'), { code: 503 }))
      .mockResolvedValueOnce({ count: 6 })
    const result = reportVisit(request)
    await vi.advanceTimersByTimeAsync(499)
    expect(request).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(request).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1499)
    expect(request).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(await result).toEqual({ count: 6 })
    expect(request).toHaveBeenCalledTimes(3)
    expect(request.mock.calls[0]?.[0]).toEqual(request.mock.calls[2]?.[0])
  })

  it('持续故障最多尝试三次，取消退避等待不会发起下次请求', async () => {
    vi.useFakeTimers()
    const request = vi.fn().mockRejectedValue(new Error('network'))
    const failed = expect(reportVisit(request)).rejects.toThrow('network')
    await vi.advanceTimersByTimeAsync(2000)
    await failed
    expect(request).toHaveBeenCalledTimes(3)
    request.mockClear()
    const controller = new AbortController()
    const canceled = expect(reportVisit(request, { signal: controller.signal })).rejects.toThrow()
    await vi.advanceTimersByTimeAsync(0)
    controller.abort()
    await canceled
    await vi.advanceTimersByTimeAsync(2000)
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('无效旧身份被替换为持久 UUID，存储拒绝时不上报', async () => {
    localStorage.setItem('visit:visitorId', 'broken')
    expect(getVisitorId()).toMatch(/^[0-9a-f-]{36}$/)
    localStorage.clear()
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('denied') })
    const request = vi.fn()
    await expect(reportVisit(request)).rejects.toThrow('无法保存访客标识')
    expect(request).not.toHaveBeenCalled()
    expect(getVisitorId()).toBeNull()
  })

  it('持久化游客身份并在请求重试时复用同一事件', async () => {
    const request = vi.fn()
      .mockRejectedValueOnce(Object.assign(new Error('server'), { code: 500 }))
      .mockResolvedValueOnce({ count: 4 })
    const first = getVisitorId()
    const result = await reportVisit(request, { maxAttempts: 2, backoffMs: 0 })
    expect(getVisitorId()).toBe(first)
    expect(request).toHaveBeenCalledTimes(2)
    expect(request.mock.calls[0][0]).toEqual(request.mock.calls[1][0])
    expect(result).toEqual({ count: 4 })
  })

  it('取消请求后不继续重试', async () => {
    const controller = new AbortController()
    const request = vi.fn().mockImplementation(() => {
      controller.abort()
      return Promise.reject(Object.assign(new Error('network'), { code: 0 }))
    })
    await expect(reportVisit(request, { signal: controller.signal, maxAttempts: 3, backoffMs: 0 })).rejects.toThrow()
    expect(request).toHaveBeenCalledTimes(1)
  })
})
