import { afterEach, describe, expect, it, vi } from 'vitest'
import { getCurrentLocation } from './geolocation'

describe('getCurrentLocation', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
  })

  it('开发演示使用杭州示例位置，不依赖设备定位', async () => {
    vi.stubEnv('DEV', true)
    const getCurrentPosition = vi.fn()
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } })
    vi.spyOn(Math, 'random').mockReturnValue(0.5)

    await expect(getCurrentLocation()).resolves.toEqual({
      longitude: 120.164,
      latitude: 30.274,
      simulatedAddress: '杭州武林广场一带',
    })
    expect(getCurrentPosition).not.toHaveBeenCalled()
  })

  it('生产模式通过浏览器获取设备位置', async () => {
    vi.stubEnv('DEV', false)
    const getCurrentPosition = vi.fn((success: PositionCallback) => {
      success({ coords: { longitude: 116.4, latitude: 39.9 } } as GeolocationPosition)
    })
    vi.stubGlobal('navigator', { geolocation: { getCurrentPosition } })

    await expect(getCurrentLocation()).resolves.toEqual({ longitude: 116.4, latitude: 39.9 })
  })
})
