import { expect, test } from '@playwright/test'

test('秒杀失败弹出后端信息，提示框在视口内且可以继续领取', async ({ page }, testInfo) => {
  await page.addInitScript(() => sessionStorage.setItem('token', 'e2e-test-token'))
  let message = '活动未开始！'
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) {
      await route.continue()
      return
    }
    const json = path === '/api/shop/42'
      ? { code: 1, data: { id: 42, name: '测试门店', score: 48 } }
      : path === '/api/voucher/list/42'
        ? { code: 1, data: [{ id: 17, title: '限时券', type: 1, stock: 1, payValue: 100, actualValue: 200 }] }
        : path === '/api/voucher-order/seckill/17'
          ? { code: 0, msg: message, data: null }
          : { code: 1, data: null }
    await route.fulfill({ json })
  })
  await page.goto(`${process.env.E2E_BASE_URL || ''}/shops/42`)
  const button = page.getByRole('button', { name: '立即秒杀' })
  for (const msg of ['活动未开始！', '活动已结束！', '优惠券不存在！', '库存不足！', '不允许重复下单', '用户已经购买过一次！']) {
    message = msg
    await button.click()
    const popup = page.locator('.el-message--error').filter({ hasText: msg })
    await expect(popup).toBeVisible()
    await expect(popup).toHaveCSS('position', 'fixed')
    await expect(button).toBeEnabled()
    await expect(popup).toBeInViewport({ ratio: 1 })
    const box = await popup.boundingBox()
    expect(box!.width).toBeLessThanOrEqual(360)
    expect(box!.height).toBeLessThanOrEqual(80)
    const icon = await popup.locator('svg').boundingBox()
    expect(icon!.width).toBeLessThanOrEqual(24)
    expect(icon!.height).toBeLessThanOrEqual(24)
    if (msg === '库存不足！') {
      await page.screenshot({ path: testInfo.outputPath('voucher-error.png'), fullPage: true })
    }
    await expect(popup).toHaveCount(0, { timeout: 6000 })
  }
})
