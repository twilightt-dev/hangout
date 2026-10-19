<template>
  <main class="section-list">
    <RouterLink :to="`/shops/${shopId}`">← 返回店铺详情</RouterLink>
    <h1>{{ title }}</h1>
    <PageState v-if="!page && loading" state="loading"/>
    <p v-if="error" role="alert">{{ error }} <button type="button" @click="load(!!page)">重试</button></p>
    <template v-if="page">
      <ShopDetailReadSection v-if="kind === 'vouchers'" :shop-id="shopId" :vouchers="voucherSection" list-only :claiming-id="claimingId" @claim="claim"/>
      <ShopDetailReadSection v-else-if="kind === 'blogs'" :shop-id="shopId" :blogs="blogSection" list-only :pending-blog-id="pendingBlogId" @like="like"/>
      <ShopDetailReadSection v-else-if="kind === 'related-shops'" :shop-id="shopId" :related="relatedSection" list-only/>
      <ShopSetmealSection v-else-if="kind === 'setmeals'" :shop-id="shopId" :preview="mealSection" list-only/>
      <ShopReviewSection v-else-if="kind === 'reviews'" :shop-id="shopId" :preview="reviewSection" list-only/>
      <RecommendDishSection v-else-if="kind === 'recommendations'" :shop-id="shopId" :preview="dishSection" list-only @changed="reset"/>
      <p v-if="!page.records.length && !error">暂无更多内容</p>
      <button v-if="current < page.pages" type="button" data-test="list-more" :disabled="loading" @click="load(true)">{{ loading ? '加载中…' : '加载更多' }}</button>
      <p v-else-if="page.records.length">已显示全部 {{ page.total }} 条</p>
    </template>
  </main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getShopVoucherPage, getShopBlogPage, getRelatedShopPage, type RelatedShop, type ShopPage, type Section } from '@/api/shopDetail'
import { getShopSetmeals } from '@/api/shopSetmeal'
import { getShopReviews } from '@/api/shopReview'
import { getRecommendDishes, type RecommendDish } from '@/api/recommendDish'
import type { Voucher, Blog, ShopSetmeal, ShopReview, ShopReviewPage } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import ShopDetailReadSection from '@/components/shop/ShopDetailReadSection.vue'
import ShopSetmealSection from '@/components/shop/ShopSetmealSection.vue'
import ShopReviewSection from '@/components/shop/ShopReviewSection.vue'
import RecommendDishSection from '@/components/shop/RecommendDishSection.vue'
import { useShopActions } from '@/composables/useShopActions'
import PageState from '@/components/common/PageState.vue'
type Row = Voucher | Blog | RelatedShop | ShopSetmeal | ShopReview | RecommendDish
const names: Record<string,string> = { vouchers:'店内优惠',setmeals:'店铺套餐',recommendations:'推荐菜',reviews:'店铺评价',blogs:'达人探店','related-shops':'周边推荐' }
const route = useRoute(), auth = useAuthStore()
const shopId = computed(() => Number(route.params.id)), kind = computed(() => String(route.params.section))
const title = computed(() => names[kind.value] || '店铺列表')
const page = ref<ShopPage<Row>>(), current = ref(0), loading = ref(false), error = ref('')
let epoch = 0
let controller: AbortController | undefined
const ok = <T,>(value?: ShopPage<T>): Section<ShopPage<T>> => ({ status:value?.records.length ? 'SUCCESS' : 'EMPTY',data:value || null })
const voucherSection = computed(() => ok(page.value as ShopPage<Voucher> | undefined))
const blogSection = computed(() => ok(page.value as ShopPage<Blog> | undefined))
const relatedSection = computed(() => ok(page.value as ShopPage<RelatedShop> | undefined))
const mealSection = computed(() => ok(page.value as ShopPage<ShopSetmeal> | undefined))
const reviewSection = computed(() => ok(page.value as ShopReviewPage | undefined))
const dishSection = computed(() => ok(page.value as ShopPage<RecommendDish> | undefined))
async function refreshList() { current.value=1; await load() }
const { claimingId, pendingBlogId, claim, like } = useShopActions(shopId, refreshList, refreshList)
async function load(more = false) {
  if (loading.value) return
  const version = epoch, id = shopId.value, section = kind.value, target = more ? current.value + 1 : Math.max(1,current.value)
  if (!Number.isSafeInteger(id) || id < 1 || !names[section]) { error.value = '列表地址无效'; return }
  controller?.abort(); controller = new AbortController(); loading.value = true; error.value = ''
  try {
    let result: ShopPage<Row>
    if (section === 'vouchers') result = await getShopVoucherPage(id,target,10,controller.signal)
    else if (section === 'setmeals') result = await getShopSetmeals(id,controller.signal,target,10)
    else if (section === 'blogs') result = await getShopBlogPage(id,target,10,controller.signal)
    else if (section === 'recommendations') result = await getRecommendDishes(id,target,12,controller.signal)
    else if (section === 'reviews') result = await getShopReviews(id,target)
    else {
      const response = await getRelatedShopPage(id,target,10,controller.signal)
      if (!response.data) throw new Error(response.message || '周边推荐暂不可用')
      result = response.data
    }
    if (version !== epoch) return
    // 更多列表从第一页独立开始；同一列表加载后续页时按 ID 去重，应对排名变化。
    const merged = more ? [...(page.value?.records || []),...result.records] : result.records
    const seen = new Set<number>()
    page.value = { ...result,records:merged.filter(row => row.id != null && !seen.has(row.id) && !!seen.add(row.id)) }
    current.value = target
  } catch (failure) { if (version === epoch) error.value = failure instanceof Error ? failure.message : '加载失败，请重试' }
  finally { if (version === epoch) loading.value = false }
}
function reset() { epoch++; controller?.abort(); loading.value=false; page.value=undefined; current.value=0; void load() }
watch([shopId,kind,() => auth.isAuthenticated,() => auth.user?.id],reset,{immediate:true})
onBeforeUnmount(() => { epoch++;controller?.abort() })
</script>
<style scoped>
.section-list{max-width:960px;margin:auto}.section-list>button{display:block;margin:24px auto;min-height:44px;padding:8px 24px}.section-list>p{text-align:center;color:var(--color-muted)}[role=alert]{padding:16px;border:1px solid var(--color-border)}
</style>
