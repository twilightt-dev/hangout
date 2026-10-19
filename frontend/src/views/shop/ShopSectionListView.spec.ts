import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ShopSectionListView from './ShopSectionListView.vue'
const api=vi.hoisted(()=>({blogs:vi.fn(),detail:vi.fn(),push:vi.fn()}))
const route=reactive({params:{id:'8',section:'blogs'}})
vi.mock('vue-router',()=>({useRoute:()=>route,useRouter:()=>({push:api.push})}))
vi.mock('@/stores/auth',()=>({useAuthStore:()=>({isAuthenticated:false,user:null})}))
vi.mock('@/api/shopDetail',async original=>({...await original<typeof import('@/api/shopDetail')>(),getShopBlogPage:api.blogs,getShopDetail:api.detail}))
const records=(name:string,id=1)=>({records:[{id,title:name,name:'作者',isLike:false}],total:11,current:id,size:10,pages:2})
describe('查看更多独立分页',()=>{
 beforeEach(()=>{route.params.section='blogs';api.blogs.mockReset();api.detail.mockReset()})
 it('从第一页独立开始，第二页失败保留已加载记录，重试目标页',async()=>{
   api.blogs.mockResolvedValueOnce(records('第一页')).mockRejectedValueOnce(new Error('暂不可用')).mockResolvedValueOnce(records('第二页',2))
   const wrapper=mount(ShopSectionListView,{global:{stubs:{RouterLink:{template:'<a><slot/></a>'}}}})
   await flushPromises();expect(wrapper.text()).toContain('第一页')
   expect(api.blogs.mock.calls[0]?.slice(0,3)).toEqual([8,1,10]);expect(api.detail).not.toHaveBeenCalled()
   await wrapper.get('[data-test="list-more"]').trigger('click');await flushPromises()
   expect(wrapper.text()).toContain('第一页');expect(wrapper.text()).toContain('暂不可用')
   await wrapper.get('[role="alert"] button').trigger('click');await flushPromises()
   expect(api.blogs.mock.calls[2]?.slice(0,3)).toEqual([8,2,10])
   expect(wrapper.text()).toContain('第一页');expect(wrapper.text()).toContain('第二页')
   wrapper.unmount()
 })
})
