import { shopDetail, section } from './fixtures/shopDetail'
import { expect, test } from '@playwright/test'

const result = <T>(data: T) => ({ code: 1, data })

async function mockApi(page: import('@playwright/test').Page) {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }
    const json = url.pathname === '/api/shop-type/list' ? result([{ id: 1, name: '美食' }])
      : url.pathname === '/api/blog/hot' ? result([])
      : url.pathname === '/api/shop/of/type' ? result([])
      : url.pathname === '/api/shop/of/name' ? result([])
      : url.pathname === '/api/shop/42/detail' ? result(shopDetail({ id: 42, name: '星球咖啡', images: '' }, { statistics: section({totalReviews:2,averageScore:4.8}) }))
      : url.pathname === '/api/voucher/list/42' ? result([])
      : result(null)
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(json) })
  })
}

test.describe('探店星球核心交付回归', () => {
  test.beforeEach(async ({ page }) => {
    page.on('pageerror', (error) => console.error('浏览器页面异常：', error.message))
    await mockApi(page)
  })

  test('首页展示探店星球品牌和主导航', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('link', { name: '探店星球首页' })).toBeVisible()
    await expect(page).toHaveTitle('探店星球')
  })

  test('移动端显示底部导航且隐藏桌面侧栏', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅在移动项目验证')
    await page.goto('/')
    await expect(page.locator('.bottom-nav')).toBeVisible()
    await expect(page.locator('.desktop-sidebar')).toBeHidden()
    const nav = page.locator('.bottom-nav')
    expect(await nav.boundingBox()).toMatchObject({ width: expect.any(Number) })
    await page.screenshot({ path: 'test-results/mobile-home.png', fullPage: true })
  })

  test('桌面端显示左右栏且隐藏底部导航', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop', '仅在桌面项目验证')
    await page.goto('/')
    await expect(page.locator('.desktop-sidebar')).toBeVisible()
    await expect(page.locator('.desktop-rail')).toBeVisible()
    await expect(page.locator('.bottom-nav')).toBeHidden()
    const shell = page.locator('.shell-body')
    const columns = await shell.evaluate((element) => getComputedStyle(element).gridTemplateColumns)
    expect(columns.split(' ').length).toBeGreaterThanOrEqual(3)
    await page.screenshot({ path: 'test-results/desktop-home.png', fullPage: true })
  })

  test('门店深层路由可直接打开', async ({ page }) => {
    await page.goto('/shops/42')
    await expect(page.getByRole('heading', { name: '星球咖啡' })).toBeVisible()
    await expect(page.getByText('★ 4.8')).toBeVisible()
  })

  test('未登录访问受保护路由时跳转登录并保留 redirect', async ({ page }) => {
    await page.goto('/publish')
    await expect(page).toHaveURL(/\/login\?redirect=/)
    expect(new URL(page.url()).searchParams.get('redirect')).toBe('/publish')
    await expect(page.getByRole('heading', { name: '验证码登录' })).toBeVisible()
  })

  test('桌面端从“我的”跳转登录时表单不会被侧栏网格压窄', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'desktop', '仅在桌面项目验证')
    await page.goto('/me')
    await expect(page).toHaveURL(/\/login\?redirect=/)
    const panel = page.locator('.auth-panel')
    await expect(panel).toBeVisible()
    const box = await panel.boundingBox()
    expect(box?.width).toBeGreaterThanOrEqual(360)
  })
})
