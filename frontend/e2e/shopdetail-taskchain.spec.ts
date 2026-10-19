import { expect, test } from '@playwright/test'
test('六区聚合、局部重试与独立查看更多在桌面和手机可用',async({page},testInfo)=>{
 const blogs=Array.from({length:11},(_,i)=>({id:i+1,shopId:15,title:`探店笔记${i+1}`,name:'食客',isLike:false,liked:3,images:''}))
 const pageOf=(records:unknown[],total=records.length,size=10,current=1)=>({records,total,size,current,pages:Math.ceil(total/size)})
 const section=(data:unknown)=>({status:'SUCCESS',data})
 let detailCalls=0, voucherCalls=0
 await page.route('**/api/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname
  if (!path.startsWith('/api/')) return route.continue()
  const ok=(data:unknown)=>route.fulfill({json:{code:1,data}})
  if(path==='/api/shop/15/detail'){
   detailCalls++
   return ok({shop:{id:15,name:'街角小馆',address:'江南大道15号',images:'',openHours:'10:00—22:00'},
    vouchers:{status:'FAILED',data:null,message:'优惠券暂时不可用'},setmeals:section(pageOf([])),
    recommendations:section(pageOf([])),reviews:section(pageOf([])),statistics:section({totalReviews:0,averageScore:null}),
    blogs:section(pageOf(blogs.slice(0,4),11,4)),relatedShops:{status:'UNAVAILABLE',data:null,message:'缺少位置信息，暂无法推荐周边店铺'},
    decisionTags:section([{code:'TASTY',name:'味道好',userCount:2}]),reviewTags:section([]),recommendationStateAvailable:true})
  }
  if(path==='/api/shop/15/vouchers'){voucherCalls++;return ok(pageOf([{id:1,title:'到店优惠',payValue:1000,actualValue:1200,type:0,availability:'AVAILABLE'}]))}
  if(path==='/api/shop/15/blogs'){const current=Number(url.searchParams.get('current'));return ok(pageOf(blogs.slice((current-1)*10,current*10),11,10,current))}
  if(path.endsWith('/visit'))return ok({count:3})
  return ok([])
 })
 await page.goto('/shops/15')
 await expect(page.getByRole('heading',{name:'街角小馆'})).toBeVisible()
 await expect(page.getByText('味道好',{exact:true})).toBeVisible()
 await expect(page.locator('.blog-card')).toHaveCount(4)
 await page.locator('[aria-labelledby="read-vouchers-title"]').getByRole('button',{name:'重试'}).click()
 await expect(page.getByText('到店优惠',{exact:true})).toBeVisible()
 expect(detailCalls).toBe(1);expect(voucherCalls).toBe(1)
 await page.screenshot({path:testInfo.outputPath('shopdetail-six-sections.png'),fullPage:true})
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true)
 await page.locator('[aria-labelledby="read-blogs-title"]').getByRole('button',{name:'查看更多'}).click()
 await expect(page).toHaveURL(/lists\/blogs/)
 await expect(page.locator('.blog-card')).toHaveCount(10)
 await page.getByRole('button',{name:'加载更多',exact:true}).click()
 await expect(page.locator('.blog-card')).toHaveCount(11)
 expect(detailCalls).toBe(1)
})
