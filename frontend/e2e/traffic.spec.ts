import { expect, test, type Page } from '@playwright/test'

type Visit = { kind: 'blog' | 'shop'; visitorId: string; eventId: string }
type TrafficOptions = { authenticated?: boolean; failures?: number }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

async function trafficApi(page: Page, options: TrafficOptions = {}) {
  const visits: Visit[] = []
  const shopVisitors = new Set<string>()
  let blogViews = 20
  let detailReads = 0
  let shopReads = 0
  let liked = false
  if (options.authenticated) {
    await page.addInitScript(() => {
      sessionStorage.setItem('token', 'traffic-test-token')
      sessionStorage.setItem('userInfo', JSON.stringify({ id: 1, nickName: '访问测试用户' }))
    })
  }
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    // Vite 源码模块路径也包含 /api/，这里只模拟真正的后端请求。
    if (!path.startsWith('/api/')) {
      await route.continue()
      return
    }
    const kind = path === '/api/blog/88/visit' ? 'blog' : path === '/api/shop/15/visit' ? 'shop' : undefined
    if (kind) {
      expect(request.method()).toBe('POST')
      const payload = request.postDataJSON() as { visitorId: string; eventId: string }
      expect(payload.visitorId).toMatch(uuid)
      expect(payload.eventId).toMatch(uuid)
      visits.push({ kind, ...payload })
      if (visits.length <= (options.failures ?? 0)) {
        await route.fulfill({ status: 503, json: { code: 0, msg: '访问统计暂时不可用' } })
        return
      }
      if (kind === 'blog') blogViews += 1
      else shopVisitors.add(payload.visitorId)
      await route.fulfill({ json: { code: 1, data: { count: kind === 'blog' ? blogViews : shopVisitors.size } } })
      return
    }
    let data: unknown = null
    if (path === '/api/blog/view/88') {
      detailReads += 1
      data = {
        id: 88, userId: 2, shopId: 15, name: '探店记录作者', title: '周末探店记录',
        content: '窗边的座位很舒服，这次点的招牌菜值得再来。', images: '',
        liked: liked ? 1 : 0, isLike: liked, createTime: '2026-10-09 10:00:00',
      }
    } else if (path === '/api/blog/like/88') liked = !liked
    else if (path === '/api/blog/likes/88') data = []
    else if (path === '/api/blog/88/comments') data = { records: [], current: 1, pages: 1, total: 0 }
    else if (path === '/api/shop/15') {
      shopReads += 1
      data = { id: 15, name: '街角小馆', area: '滨江区', address: '江南大道 15 号', images: '', score: 46, comments: 18, openHours: '10:00—22:00' }
    } else if (path === '/api/voucher/list/15') data = []
    else if (path === '/api/follow/status/2') data = false
    else if (path === '/api/user/me') data = { id: 1, nickName: '访问测试用户' }
    await route.fulfill({ json: { code: 1, data } })
  })
  return { visits, getDetailReads: () => detailReads, getShopReads: () => shopReads }
}

function statistic(page: Page) {
  return page.locator('[data-test="visit-statistic"]')
}

async function checkLayout(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  const box = await statistic(page).boundingBox()
  expect(box).not.toBeNull()
  if (box) {
    expect(box.x).toBeGreaterThanOrEqual(0)
    expect(box.x + box.width).toBeLessThanOrEqual(page.viewportSize()!.width)
  }
}

test('博客一次展示上报一次 PV，关联门店与点赞刷新不额外上报', async ({ page }, testInfo) => {
  const api = await trafficApi(page, { authenticated: true })
  await page.goto('/blogs/88')
  await expect(statistic(page)).toHaveText('累计浏览 21 次')
  await expect(page.getByRole('heading', { name: '周末探店记录' })).toBeVisible()
  expect(api.visits).toHaveLength(1)
  expect(api.getShopReads()).toBe(1)
  expect(api.visits.some(visit => visit.kind === 'shop')).toBe(false)
  await page.locator('[data-test="blog-like"]').click()
  await expect(page.locator('[data-test="blog-like"]')).toHaveAttribute('aria-pressed', 'true')
  expect(api.getDetailReads()).toBe(2)
  expect(api.visits).toHaveLength(1)
  await statistic(page).scrollIntoViewIfNeeded()
  await checkLayout(page)
  await page.screenshot({ path: testInfo.outputPath('blog-pv.png'), fullPage: true })
  await page.reload()
  await expect(statistic(page)).toHaveText('累计浏览 22 次')
  expect(api.visits).toHaveLength(2)
  expect(api.visits[1]!.visitorId).toBe(api.visits[0]!.visitorId)
  expect(api.visits[1]!.eventId).not.toBe(api.visits[0]!.eventId)
})

test('店铺重复展示使用同一访客，登录前后累计 UV 不增加', async ({ page }, testInfo) => {
  const api = await trafficApi(page)
  await page.goto('/shops/15')
  await expect(statistic(page)).toHaveText('累计访客约 1 位')
  await expect(page.getByRole('heading', { name: '街角小馆' })).toBeVisible()
  await page.evaluate(() => {
    sessionStorage.setItem('token', 'traffic-test-token')
    sessionStorage.setItem('userInfo', JSON.stringify({ id: 1, nickName: '访问测试用户' }))
  })
  await page.reload()
  await expect(statistic(page)).toHaveText('累计访客约 1 位')
  expect(api.visits).toHaveLength(2)
  expect(api.visits.every(visit => visit.kind === 'shop')).toBe(true)
  expect(api.visits[1]!.visitorId).toBe(api.visits[0]!.visitorId)
  expect(api.visits[1]!.eventId).not.toBe(api.visits[0]!.eventId)
  await checkLayout(page)
  await page.screenshot({ path: testInfo.outputPath('shop-uv.png'), fullPage: true })
})

for (const kind of ['blog', 'shop'] as const) {
  const path = kind === 'blog' ? '/blogs/88' : '/shops/15'
  const success = kind === 'blog' ? '累计浏览 21 次' : '累计访客约 1 位'
  const title = kind === 'blog' ? '周末探店记录' : '街角小馆'

  test(`${kind === 'blog' ? '博客' : '店铺'}统计暂时失败时复用原事件重试`, async ({ page }) => {
    const api = await trafficApi(page, { failures: 1 })
    await page.goto(path)
    await expect(statistic(page)).toHaveText(success)
    expect(api.visits).toHaveLength(2)
    expect(api.visits[1]).toEqual(api.visits[0])
  })

  test(`${kind === 'blog' ? '博客' : '店铺'}统计持续失败仍显示详情与暂无统计`, async ({ page }, testInfo) => {
    const api = await trafficApi(page, { failures: Infinity })
    await page.goto(path)
    await expect(statistic(page)).toHaveText('暂无统计')
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    expect(api.visits).toHaveLength(3)
    expect(api.visits.every(visit => visit.eventId === api.visits[0]!.eventId && visit.visitorId === api.visits[0]!.visitorId)).toBe(true)
    await checkLayout(page)
    await page.screenshot({ path: testInfo.outputPath(`${kind}-unavailable.png`), fullPage: true })
  })
}
