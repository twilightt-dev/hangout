import { expect, test, type Page } from '@playwright/test'

async function feedApi(page: Page, failFirst = false, total = 23, pageSize = 10) {
  const time = Date.now() - 60_000
  const blogs = Array.from({ length: total }, (_, index) => ({
    id: 100 - index, userId: 2, name: '关注的博主', title: `关注动态第 ${index + 1} 篇`,
    images: '', liked: 0, createTime: new Date(time).toISOString(),
  }))
  const cursors: { lastId: number; offset: number }[] = []
  await page.addInitScript(() => { sessionStorage.setItem('token', 'feed-test-token') })
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }
    let data: unknown = null
    if (url.pathname === '/api/user/me') data = { id: 1, nickName: '我的主页' }
    else if (url.pathname === '/api/user/info/1') data = { fans: 0, followee: 1 }
    else if (url.pathname === '/api/blog/of/me') data = []
    else if (url.pathname === '/api/blog/of/follow') {
      const cursor = { lastId: Number(url.searchParams.get('lastId')), offset: Number(url.searchParams.get('offset')) }
      cursors.push(cursor)
      if (failFirst && cursors.length === 1) {
        await route.fulfill({ status: 503, json: { code: 0, msg: '暂时不可用' } })
        return
      }
      const start = cursor.lastId === time ? cursor.offset : 0
      const list = blogs.slice(start, start + pageSize)
      data = { list, minTime: time, offset: start + list.length }
    }
    await route.fulfill({ json: { code: 1, data } })
  })
  return cursors
}

test('关注动态在桌面和移动端下滑续页、到底并可刷新', async ({ page }, testInfo) => {
  const cursors = await feedApi(page)
  await page.goto('/me')
  await page.getByRole('tab', { name: '关注动态' }).click()
  await expect(page.getByText('关注动态第 1 篇', { exact: true })).toBeVisible()
  const feed = page.getByRole('region', { name: '关注动态列表' })
  for (let i = 0; i < 4 && !(await page.getByText('已经到底啦~', { exact: true }).count()); i++) {
    await expect(feed).toHaveAttribute('aria-busy', 'false')
    const previousRequests = cursors.length
    await feed.evaluate((element) => { element.scrollTop = element.scrollHeight; element.dispatchEvent(new Event('scroll')) })
    await expect.poll(() => cursors.length).toBeGreaterThan(previousRequests)
    await expect(feed).toHaveAttribute('aria-busy', 'false')
  }
  await expect(page.getByText('已经到底啦~', { exact: true })).toBeVisible()
  await expect(feed.locator('.blog-card')).toHaveCount(23)
  expect(cursors.map(cursor => cursor.offset)).toEqual([0, 10, 20, 23])
  await feed.evaluate((element) => { element.scrollTop = element.scrollHeight })
  await page.getByText('已经到底啦~', { exact: true }).scrollIntoViewIfNeeded()
  await expect(page.getByText('已经到底啦~', { exact: true })).toBeInViewport()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('feed-bottom.png') })
  await page.getByRole('button', { name: '刷新动态' }).click()
  await expect(page.getByText('关注动态第 1 篇', { exact: true })).toBeVisible()
  await expect(feed.locator('.blog-card')).toHaveCount(10)
  expect(cursors.at(-1)?.offset).toBe(0)
})

test('关注动态首屏加载失败显示重试，不显示到底', async ({ page }) => {
  await feedApi(page, true)
  await page.goto('/me')
  await page.getByRole('tab', { name: '关注动态' }).click()
  await expect(page.getByText('关注动态加载失败，请稍后重试。')).toBeVisible()
  await expect(page.getByText('已经到底啦~', { exact: true })).toBeHidden()
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByText('关注动态第 1 篇', { exact: true })).toBeVisible()
})

test('关注动态首屏不足以滚动时自动续页直到到底', async ({ page }) => {
  const cursors = await feedApi(page, false, 2, 1)
  await page.goto('/me')
  await page.getByRole('tab', { name: '关注动态' }).click()
  await expect(page.getByText('已经到底啦~', { exact: true })).toBeVisible()
  await expect(page.getByRole('region', { name: '关注动态列表' }).locator('.blog-card')).toHaveCount(2)
  expect(cursors.map(cursor => cursor.offset)).toEqual([0, 1, 2])
})
