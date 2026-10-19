import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import ShopDetailView from './ShopDetailView.vue'
import type { ShopDetail, ShopPage, Section } from '@/api/shopDetail'

const api = vi.hoisted(() => ({
  detail:vi.fn(), vouchers:vi.fn(), blogs:vi.fn(), related:vi.fn(), meals:vi.fn(), dishes:vi.fn(),
  reviews:vi.fn(), tags:vi.fn(), publish:vi.fn(), sendVisit:vi.fn(), claim:vi.fn(), like:vi.fn(),
  push:vi.fn(), error:vi.fn(), auth:{isAuthenticated:false,user:null as {id:number} | null},
}))
vi.mock('@/api/shopDetail',async original => ({ ...await original<typeof import('@/api/shopDetail')>(),getShopDetail:api.detail,getShopVoucherPage:api.vouchers,getShopBlogPage:api.blogs,getRelatedShopPage:api.related }))
vi.mock('@/api/shopSetmeal',()=>({getShopSetmeals:api.meals}))
vi.mock('@/api/recommendDish',()=>({getRecommendDishes:api.dishes,publishRecommendDish:vi.fn(),recommendDish:vi.fn()}))
vi.mock('@/api/shopReview',()=>({getShopReviews:api.reviews,getReviewTags:api.tags,publishShopReview:api.publish}))
vi.mock('@/api/shop',()=>({seckillVoucher:api.claim}))
vi.mock('@/api/blog',()=>({likeBlog:api.like}))
vi.mock('@/api/traffic',async original=>({...await original<typeof import('@/api/traffic')>(),sendVisit:api.sendVisit}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>api.auth}))
const route=reactive({params:{id:'8'},fullPath:'/shops/8'})
vi.mock('vue-router',()=>({useRoute:()=>route,useRouter:()=>({push:api.push})}))
vi.mock('element-plus',()=>({ElMessage:{error:api.error,success:vi.fn(),warning:vi.fn(),info:vi.fn()}}))
const page=<T,>(records:T[]=[]):ShopPage<T>=>({records,total:records.length,current:1,size:10,pages:records.length?1:0})
const section=<T,>(data:T):Section<T>=>({status:'SUCCESS',data})
function detail():ShopDetail {
  return {shop:{id:8,name:'巷口咖啡',score:10},vouchers:section(page()),setmeals:section(page()),
    recommendations:section(page()),reviews:section(page()),statistics:section({totalReviews:2,averageScore:4.8}),
    blogs:section(page()),decisionTags:section([{code:'TASTY',name:'味道好',userCount:2}]),
    reviewTags:section([]),relatedShops:section(page()),recommendationStateAvailable:true}
}
function deferred<T>() {let resolve!:(v:T)=>void;const promise=new Promise<T>(done=>{resolve=done});return {promise,resolve}}
const wrappers:ReturnType<typeof mount>[]=[]
function create(){const wrapper=mount(ShopDetailView,{global:{stubs:{RouterLink:{template:'<a><slot/></a>'}}}});wrappers.push(wrapper);return wrapper}
describe('商详任务链首屏',()=>{
  beforeEach(()=>{
    vi.clearAllMocks();localStorage.clear();route.params.id='8';route.fullPath='/shops/8'
    api.auth.isAuthenticated=false;api.auth.user=null
    api.detail.mockReset().mockResolvedValue(detail());api.tags.mockResolvedValue([])
    api.vouchers.mockResolvedValue(page());api.blogs.mockResolvedValue(page());api.meals.mockResolvedValue(page())
    api.dishes.mockResolvedValue(page());api.reviews.mockResolvedValue({...page(),averageScore:4.8})
    api.sendVisit.mockReset().mockResolvedValue({count:7});api.claim.mockReset().mockResolvedValue(99)
  })
  afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
  it('一次聚合取得六区，不重复请求各列表，评分取新统计',async()=>{
    const wrapper=create();await flushPromises()
    expect(wrapper.text()).toContain('巷口咖啡');expect(wrapper.text()).toContain('★ 4.8')
    expect(wrapper.text()).toContain('味道好');expect(wrapper.text()).toContain('达人探店');expect(wrapper.text()).toContain('周边推荐')
    expect(api.detail).toHaveBeenCalledTimes(1)
    for(const list of [api.vouchers,api.blogs,api.meals,api.dishes,api.reviews])expect(list).not.toHaveBeenCalled()
    expect(api.sendVisit).toHaveBeenCalledTimes(1)
  })
  it('优惠券失败保留其他区域，通过独立接口重试',async()=>{
    api.detail.mockResolvedValue({...detail(),vouchers:{status:'FAILED',data:null,message:'优惠券加载失败'}})
    api.vouchers.mockResolvedValue(page([{id:17,title:'限时券',type:1,stock:1}]))
    const wrapper=create();await flushPromises()
    expect(wrapper.text()).toContain('巷口咖啡');expect(wrapper.text()).toContain('优惠券加载失败')
    const button=wrapper.findAll('button').find(b=>b.text()==='重试')!
    await button.trigger('click');await flushPromises()
    expect(wrapper.text()).toContain('限时券');expect(api.detail).toHaveBeenCalledTimes(1)
    expect(api.vouchers).toHaveBeenCalledWith(8,1,2)
  })
  it('评分故障不显示旧评分或零条评价',async()=>{
    api.detail.mockResolvedValue({...detail(),statistics:{status:'FAILED',data:null}})
    const wrapper=create();await flushPromises()
    expect(wrapper.text()).toContain('评价统计暂不可用');expect(wrapper.text()).not.toContain('0 条评价');expect(wrapper.get('.reviews').text()).toContain('评分暂不可用');expect(wrapper.get('.reviews').text()).not.toContain('暂无评分')
  })
  it('标签统计不完整与缺坐标明确展示',async()=>{
    api.detail.mockResolvedValue({...detail(),decisionTags:{status:'PARTIAL',data:[{code:'TASTY',name:'味道好',userCount:2}]},
      relatedShops:{status:'UNAVAILABLE',data:null,message:'缺少位置信息，暂无法推荐周边店铺'}})
    const wrapper=create();await flushPromises()
    expect(wrapper.text()).toContain('统计不完整');expect(wrapper.text()).toContain('缺少位置信息')
  })
  it('切店取消旧请求并拒绝迟到响应',async()=>{
    const old=deferred<ShopDetail>();api.detail.mockReturnValueOnce(old.promise)
    const wrapper=create();await nextTick()
    const signal=api.detail.mock.calls[0]?.[1] as AbortSignal
    route.params.id='9';await nextTick();await flushPromises()
    expect(signal.aborted).toBe(true)
    old.resolve({...detail(),shop:{id:8,name:'旧店'}});await flushPromises()
    expect(wrapper.text()).not.toContain('旧店')
  })
  it('无效地址或失败响应不上报访问',async()=>{
    route.params.id='invalid';const wrapper=create();await flushPromises()
    expect(wrapper.text()).toContain('门店地址无效');expect(api.detail).not.toHaveBeenCalled()
    expect(api.sendVisit).not.toHaveBeenCalled()
  })
  it('卸载取消访问上报',async()=>{
    api.sendVisit.mockReturnValue(new Promise(()=>{}))
    const wrapper=create();await flushPromises()
    const signal=api.sendVisit.mock.calls[0]?.[3]?.signal as AbortSignal
    wrapper.unmount();expect(signal.aborted).toBe(true)
  })
  it('游客购买先登录，已登录仍校验库存和时段',async()=>{
    const wrapper=create();await flushPromises()
    await wrapper.vm.claim({id:1,type:1,stock:2})
    expect(api.push).toHaveBeenCalledWith({name:'login',query:{redirect:'/shops/8'}})
    expect(api.claim).not.toHaveBeenCalled()
    api.auth.isAuthenticated=true
    await wrapper.vm.claim({id:1,type:1,stock:0})
    await wrapper.vm.claim({id:1,type:1,stock:2,endTime:'2020-01-01T00:00:00'})
    expect(api.claim).not.toHaveBeenCalled()
  })
  it('服务端购买失败显示原始业务消息并恢复按钮',async()=>{
    api.auth.isAuthenticated=true
    api.detail.mockResolvedValue({...detail(),vouchers:section(page([{id:17,type:1,title:'限时券',stock:1}]))})
    api.claim.mockRejectedValueOnce(new Error('库存不足'))
    const wrapper=create();await flushPromises()
    await wrapper.get('.voucher-card__claim').trigger('click');await flushPromises()
    expect(api.error).toHaveBeenCalledWith('库存不足')
    expect(wrapper.get('.voucher-card__claim').attributes('disabled')).toBeUndefined()
  })
})
