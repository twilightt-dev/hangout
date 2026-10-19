import { expect, test } from '@playwright/test'
import { pageOf } from './fixtures/shopDetail'

test('独立列表直接点赞与领取，写后刷新列表而不执行商详聚合', async ({page}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('token','list-test')
    sessionStorage.setItem('userInfo',JSON.stringify({id:7,nickName:'食客'}))
  })
  let liked=false, claimed=0, detailCalls=0
  await page.route('**/api/**', async route => {
    const path=new URL(route.request().url()).pathname
    if(!path.startsWith('/api/')) return route.continue()
    let data: unknown = null
    if(path==='/api/shop/15/blogs') data=pageOf([{id:11,shopId:15,title:'探店',liked:liked?1:0,isLike:liked}])
    else if(path==='/api/blog/like/11') liked=!liked
    else if(path==='/api/shop/15/vouchers') data=pageOf([
      {id:17,title:'限时券',type:1,stock:1,availability:'AVAILABLE'},
      {id:18,title:'预告券',type:1,stock:5,availability:'UPCOMING'},
      {id:19,title:'售罄券',type:1,stock:0,availability:'SOLD_OUT'}])
    else if(path==='/api/voucher-order/seckill/17') { claimed++;data=99 }
    else if(path.endsWith('/detail')) detailCalls++
    await route.fulfill({json:{code:1,data}})
  })
  await page.goto('/shops/15/lists/blogs')
  await page.getByRole('button',{name:'点赞',exact:true}).click()
  await expect(page.getByRole('button',{name:'取消点赞',exact:true})).toBeVisible()
  await expect(page).toHaveURL(/lists\/blogs/)
  await page.goto('/shops/15/lists/vouchers')
  await expect(page.getByRole('button',{name:'尚未开始'})).toBeDisabled()
  await expect(page.getByRole('button',{name:'已售罄'})).toBeDisabled()
  await page.getByRole('button',{name:'立即秒杀'}).click()
  await expect(page.getByText('领取成功，订单号：99')).toBeVisible()
  expect(claimed).toBe(1)
  expect(detailCalls).toBe(0)
})
