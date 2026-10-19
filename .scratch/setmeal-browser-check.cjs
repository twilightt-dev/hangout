const { chromium, expect } = require('../frontend/node_modules/@playwright/test');

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    for (const [name, viewport] of [['desktop', { width: 1440, height: 1000 }], ['mobile', { width: 390, height: 844 }]]) {
      const page = await browser.newPage({ viewport });
      let reads = 0;
      let failure = false;
      await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        if (!path.startsWith('/api/')) return route.continue();
        let data = [];
        if (path === '/api/shop/8') data = { id: 8, name: '巷口小馆', area: '滨江区', address: '街角8号', openHours: '10:00—22:00' };
        if (path === '/api/shop/8/visit') data = { count: 8 };
        if (path === '/api/shop/8/reviews') data = { records: [], total: 0, current: 1, size: 10, pages: 0, averageScore: null };
        if (path === '/api/shop/8/setmeals') {
          reads++;
          if (failure) return route.fulfill({ status: 503, json: { code: 0, msg: '服务暂不可用' } });
          data = [{ id: 1, shopId: 8, name: '99元双人餐', coverImage: '/missing-setmeal.png', price: 9900, originalPrice: 12900, people: 2,
            description: '固定菜品，一起分享好味道', rules: '无需预约\n周末可用，长文本使用规则也应保持在套餐卡片内正常换行。'.repeat(3),
            items: [{ name: '招牌烤鱼', quantity: 1, unit: '份' }, { name: '米饭', quantity: 2, unit: '碗' }] }];
        }
        return route.fulfill({ json: { code: 1, data } });
      });
      await page.goto('http://127.0.0.1:4176/shops/8');
      await expect(page.getByRole('heading', { name: '99元双人餐' })).toBeVisible();
      await expect(page.getByRole('img', { name: '99元双人餐暂无图片' })).toBeVisible();
      const summary = page.locator('.setmeal-summary');
      await summary.focus();
      await page.keyboard.press('Enter');
      await expect(page.locator('[data-test="setmeal-items"]')).toBeVisible();
      await expect(page.locator('[data-test="setmeal-rules"]')).toBeVisible();
      if (reads !== 1) throw new Error('展开不应重复查询');
      if (!(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))) throw new Error('页面横向溢出');
      await page.screenshot({ path: `.scratch/setmeal-${name}.png`, fullPage: true });
      failure = true;
      await page.reload();
      await expect(page.getByText('套餐加载失败，请稍后重试。')).toBeVisible();
      await expect(page.getByRole('heading', { name: '巷口小馆' })).toBeVisible();
      await expect(page.getByRole('heading', { name: '店铺评价' })).toBeVisible();
      failure = false;
      await page.getByRole('button', { name: '重试', exact: true }).click();
      await expect(page.getByRole('heading', { name: '99元双人餐' })).toBeVisible();
      console.log(`${name}：键盘展开、封面占位、无横向溢出、失败隔离和重试通过`);
      await page.close();
    }
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
