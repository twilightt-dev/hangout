import { shopDetail, section as resultSection, pageOf } from './fixtures/shopDetail'
import { expect, test, type Page } from '@playwright/test'

const imagePath = '/blogs/a/b/550e8400-e29b-41d4-a716-446655440000.png'

async function setupApi(page: Page, authenticated = false) {
  const dishes = Array.from({ length: 13 }, (_, index) => ({
    id: index + 1, shopId: 15, name: `推荐菜品${index + 1}`, coverImage: `/imgs${imagePath}`,
    recommendCount: 20 - index, recommended: false, createTime: '2026-10-18T12:00:00',
  }))
  const submissions: { requestId: string; name: string; coverImage: string }[] = []
  let failNextPage = true
  if (authenticated) await page.addInitScript(() => {
    sessionStorage.setItem('token', 'dish-browser-test')
    sessionStorage.setItem('userInfo', JSON.stringify({ id: 7, nickName: '推荐菜测试用户' }))
  })
  await page.route('**/imgs/blogs/**', route => route.abort())
  await page.route('**/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (!path.startsWith('/api/')) return route.continue()
    const ok = (data: unknown) => route.fulfill({ json: { code: 1, data } })
    if (path === '/api/shop/15/detail') return ok(shopDetail({ id:15,name:'街角小馆',address:'江南大道15号',images:'',openHours:'10:00—22:00' }, {recommendations:resultSection(pageOf(dishes.slice(0,6),dishes.length,6))}))
    if (path === '/api/user/me') return ok({ id: 7, nickName: '推荐菜测试用户' })
    if (path === '/api/shop/15/visit') return ok({ count: 1 })
    if (path === '/api/shop/15/reviews') return ok({ records: [], total: 0, current: 1, size: 10, pages: 0, averageScore: null })
    if (path === '/api/upload/blog/uploadImage') return ok(imagePath)
    if (path === '/api/shop/15/recommend-dishes' && request.method() === 'GET') {
      const current = Number(url.searchParams.get('current'))
      const size = Number(url.searchParams.get('size'))
      if (current === 2 && failNextPage) { failNextPage = false; return route.fulfill({ status: 503, json: { code: 0, msg: '模拟查询失败' } }) }
      return ok({ records: dishes.slice((current - 1) * size, current * size), total: dishes.length, current, size, pages: Math.ceil(dishes.length / size) })
    }
    if (path === '/api/shop/15/recommend-dishes' && request.method() === 'POST') {
      const payload = request.postDataJSON() as { requestId: string; name: string; coverImage: string }
      submissions.push(payload)
      const dish = { id: 100, shopId: 15, ...payload, recommendCount: 1, recommended: true, createTime: '2026-10-18T13:00:00' }
      dishes.push(dish)
      return ok(dish)
    }
    if (path.endsWith('/recommendation') && request.method() === 'PUT') {
      const id = Number(path.split('/').at(-2))
      const dish = dishes.find(item => item.id === id)!
      if (!dish.recommended) { dish.recommended = true; dish.recommendCount += 1 }
      return ok(dish)
    }
    return ok([])
  })
  return { submissions }
}

test('推荐菜首屏、查看更多及失败重试在桌面和手机可用', async ({ page }, testInfo) => {
  await setupApi(page)
  await page.goto('/shops/15')
  const section = page.locator('[aria-labelledby="recommend-dishes-title"]')
  await expect(section.locator('.dish-card')).toHaveCount(6)
  await section.getByRole('button', { name: '查看更多推荐菜' }).click()
  await expect(page).toHaveURL(/lists\/recommendations/)
  await expect(section.locator('.dish-card')).toHaveCount(12)
  await page.getByRole('button', { name: '加载更多', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('模拟查询失败')
  await expect(section.locator('.dish-card')).toHaveCount(12)
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await expect(section.getByRole('heading', { name: '推荐菜品13', exact: true })).toBeVisible()
  await expect(section.locator('.dish-card')).toHaveCount(13)
  await section.scrollIntoViewIfNeeded()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await section.screenshot({ path: testInfo.outputPath('recommend-dishes.png') })
})

test('登录用户可推荐已有菜品并上传发布新菜品', async ({ page }, testInfo) => {
  const api = await setupApi(page, true)
  await page.goto('/shops/15')
  const section = page.locator('[aria-labelledby="recommend-dishes-title"]')
  await section.locator('[data-test="recommend-1"]').click()
  await expect(section.locator('[data-test="recommend-1"]')).toHaveText('已推荐')
  await expect(section.locator('[data-test="recommend-1"]')).toBeDisabled()
  await expect(section.locator('.dish-card').first()).toContainText('21 人推荐')
  await section.getByRole('button', { name: '推荐一道菜' }).click()
  await section.getByLabel('菜品名称').fill('招牌烤鱼')
  await section.locator('[data-test="dish-file"]').setInputFiles({
    name: 'fish.png', mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a2k0AAAAASUVORK5CYII=', 'base64'),
  })
  await expect(section).toContainText('图片上传成功')
  await section.screenshot({ path: testInfo.outputPath('recommend-dish-form.png') })
  await section.getByRole('button', { name: '发布推荐菜', exact: true }).click()
  await expect(section).toContainText('菜品已发布，已计入你的推荐')
  expect(api.submissions).toHaveLength(1)
  expect(api.submissions[0]).toMatchObject({ name: '招牌烤鱼', coverImage: `/imgs${imagePath}` })
  expect(api.submissions[0]!.requestId).toMatch(/^[0-9a-f-]{36}$/)
})
