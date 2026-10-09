import { expect, test, type Page } from '@playwright/test'

async function mockSignApi(page: Page, failure: 'none' | 'stats' | 'refresh' | 'write' = 'none') {
  let signed = false
  let reads = 0
  let writes = 0
  await page.addInitScript(() => { sessionStorage.setItem('token', 'signin-test-token') })
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    let data: unknown = null
    if (url.pathname === '/api/user/me') data = { id: 1, nickName: '签到用户', city: '杭州' }
    else if (url.pathname === '/api/user/info/1') data = { fans: 2, followee: 3, introduce: '城市漫游' }
    else if (url.pathname === '/api/blog/of/me') data = []
    else if (url.pathname === '/api/user/sign') {
      writes++
      expect(route.request().method()).toBe('POST')
      expect(route.request().postData()).toBeNull()
      if (failure === 'write' && writes === 1) {
        await route.fulfill({ status: 503, json: { code: 0, msg: '暂时不可用' } })
        return
      }
      const alreadySigned = signed
      signed = true
      data = { date: new Date(Date.now() + 8 * 3_600_000).toISOString().slice(0, 10), alreadySigned }
    } else if (url.pathname === '/api/user/sign/stats') {
      reads++
      if ((failure === 'stats' && reads === 1) || (failure === 'refresh' && reads === 2)) {
        await route.fulfill({ status: 503, json: { code: 0, msg: '暂时不可用' } })
        return
      }
      const date = new Date(Date.now() + 8 * 3_600_000).toISOString().slice(0, 10)
      data = { date, month: date.slice(0, 7).replace('-', ''), todaySigned: signed,
        monthlyDays: signed ? 5 : 4, continuousDays: signed ? 2 : 0,
        previousMonthDays: 6, monthDifference: signed ? -1 : -2 }
    }
    await route.fulfill({ json: { code: 1, data } })
  })
  return { writes: () => writes }
}

test('个人中心签到后更新三项统计，按钮与布局在桌面及手机可用', async ({ page }, testInfo) => {
  const api = await mockSignApi(page)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/me')
  const section = page.getByRole('region', { name: '每日签到' })
  await expect(section.locator('[data-test="monthly-days"]')).toHaveText('4天')
  await expect(section.locator('[data-test="month-comparison"]')).toHaveText('比上月全月少 2 天')
  await section.getByRole('button', { name: '签到', exact: true }).click()
  await expect(section.getByRole('button', { name: '今日已签到' })).toBeDisabled()
  await expect(section.locator('[data-test="monthly-days"]')).toHaveText('5天')
  await expect(section.locator('[data-test="continuous-days"]')).toHaveText('2天')
  await expect(section.locator('[data-test="month-comparison"]')).toHaveText('比上月全月少 1 天')
  expect(api.writes()).toBe(1)
  expect(errors).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  const button = await section.getByRole('button', { name: '今日已签到' }).boundingBox()
  expect(button!.height).toBeGreaterThanOrEqual(44)
  await page.screenshot({ path: testInfo.outputPath('signin.png'), fullPage: true })
})

test('统计失败独立重试且保留个人资料', async ({ page }) => {
  await mockSignApi(page, 'stats')
  await page.goto('/me')
  await expect(page.getByRole('heading', { name: '签到用户' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveText('统计加载失败，请重试。')
  await expect(page.locator('[data-test="monthly-days"]')).toHaveCount(0)
  await page.locator('[data-test="stats-retry"]').click()
  await expect(page.locator('[data-test="monthly-days"]')).toHaveText('4天')
})

test('签到写入成功刷新失败仍禁用今日签到，重试统计可恢复', async ({ page }) => {
  const api = await mockSignApi(page, 'refresh')
  await page.goto('/me')
  await page.locator('[data-test="sign-button"]').click()
  await expect(page.getByRole('button', { name: '今日已签到' })).toBeDisabled()
  await expect(page.getByRole('alert')).toHaveText('统计加载失败，请重试。')
  await page.locator('[data-test="stats-retry"]').click()
  await expect(page.locator('[data-test="monthly-days"]')).toHaveText('5天')
  expect(api.writes()).toBe(1)
})

test('签到写入失败后允许重试', async ({ page }) => {
  const api = await mockSignApi(page, 'write')
  await page.goto('/me')
  await page.locator('[data-test="sign-button"]').click()
  await expect(page.getByRole('alert')).toHaveText('签到未确认，请重试。')
  await expect(page.locator('[data-test="sign-button"]')).toBeEnabled()
  await page.locator('[data-test="sign-button"]').click()
  await expect(page.getByRole('button', { name: '今日已签到' })).toBeDisabled()
  expect(api.writes()).toBe(2)
})
