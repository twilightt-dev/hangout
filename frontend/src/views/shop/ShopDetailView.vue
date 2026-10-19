<template>
  <section class="shop-detail-view">
    <PageState v-if="state !== 'ready'" :state="state" :message="errorMessage" @retry="load"/>
    <template v-else-if="detail">
      <section class="shop-hero">
        <div class="gallery"><img v-for="(image,index) in images" :key="image" :src="image" :alt="`${detail.shop.name || '门店'}图片 ${index + 1}`" width="720" height="480" loading="lazy"><span v-if="!images.length">暂无门店图片</span></div>
        <div class="shop-summary">
          <p>{{ detail.shop.area || '附近' }}</p><h1>{{ detail.shop.name || '未命名门店' }}</h1>
          <VisitStatistic kind="shop" :status="visitStatus" :count="visitCount"/>
          <strong>★ {{ scoreText }} <span>{{ reviewCountText }}</span></strong>
          <address>{{ detail.shop.address || '地址待补充' }}</address>
          <p v-if="detail.shop.avgPrice != null">人均 ¥{{ detail.shop.avgPrice }}</p>
          <p>营业时间：{{ detail.shop.openHours || '以门店公告为准' }}</p>
          <h2 class="tag-title">近期口碑标签</h2>
          <p v-if="!available(detail.decisionTags)">{{ detail.decisionTags.message }} <button type="button" @click="load">刷新详情</button></p>
          <template v-else><div class="tags"><span v-for="tag in detail.decisionTags.data" :key="tag.code">{{ tag.name }}</span></div>
            <small v-if="detail.decisionTags.status === 'PARTIAL'">部分来源不可用，统计不完整</small>
            <small v-else-if="!detail.decisionTags.data?.length">近期暂无足够口碑标签</small>
          </template>
        </div>
      </section>
      <ShopDetailReadSection :shop-id="shopId" :vouchers="detail.vouchers" :claiming-id="claimingId" @retry="retryVouchers" @claim="claim"/>
      <ShopSetmealSection :shop-id="shopId" :preview="detail.setmeals"/>
      <RecommendDishSection :shop-id="shopId" :preview="detail.recommendations" :state-available="detail.recommendationStateAvailable" @changed="retryRecommendations"/>
      <section class="review-tags"><span v-for="tag in detail.reviewTags.data" :key="tag.code">{{ tag.name }} · {{ tag.userCount }} 人</span><p v-if="!available(detail.reviewTags)">{{ detail.reviewTags.message }}</p></section>
      <ShopReviewSection :shop-id="shopId" :preview="reviewPreview(detail)" @changed="retryReviews" @stats="updateStats"/>
      <ShopDetailReadSection :shop-id="shopId" :blogs="detail.blogs" :pending-blog-id="pendingBlogId" @retry="retryBlogs" @like="like"/>
      <ShopDetailReadSection :shop-id="shopId" :related="detail.relatedShops" @retry="retryRelated"/>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { available, getShopDetail, getShopVoucherPage, getShopBlogPage, getRelatedShopPage, reviewPreview, type ShopDetail } from '@/api/shopDetail'
import { useAuthStore } from '@/stores/auth'
import { getRecommendDishes } from '@/api/recommendDish'
import { getShopReviews } from '@/api/shopReview'
import { useShopActions } from '@/composables/useShopActions'
import { useVisitStatistic } from '@/composables/useVisitStatistic'
import PageState from '@/components/common/PageState.vue'
import VisitStatistic from '@/components/content/VisitStatistic.vue'
import ShopDetailReadSection from '@/components/shop/ShopDetailReadSection.vue'
import ShopSetmealSection from '@/components/shop/ShopSetmealSection.vue'
import RecommendDishSection from '@/components/shop/RecommendDishSection.vue'
import ShopReviewSection from '@/components/shop/ShopReviewSection.vue'

const route = useRoute(), auth = useAuthStore()
const shopId = computed(() => Number(route.params.id))
const detail = ref<ShopDetail>()
const state = ref<'loading' | 'error' | 'ready'>('loading')
const errorMessage = ref('')
const { claimingId, pendingBlogId, claim, like } = useShopActions(shopId, retryVouchers, retryBlogs)
const visit = useVisitStatistic('shop')
const { status: visitStatus, count: visitCount } = visit
let epoch = 0, visitedId: number | undefined
let controller: AbortController | undefined
const images = computed(() => detail.value?.shop.images?.split(',').map(s => s.trim()).filter(Boolean) || [])
const scoreText = computed(() => !available(detail.value?.statistics) ? '暂不可用' : detail.value?.statistics.data?.averageScore?.toFixed(1) ?? '暂无评分')
const reviewCountText = computed(() => !available(detail.value?.statistics) ? '评价统计暂不可用' : `${detail.value?.statistics.data?.totalReviews ?? 0} 条评价`)
function updateStats(score: number | null, count: number) {
  if (detail.value && count >= 0) detail.value.statistics = { status: count ? 'SUCCESS' : 'EMPTY', data: { totalReviews: count, averageScore: score } }
}
async function load() { await fetchDetail(false) }
async function fetchDetail(keep: boolean) {
  const current = ++epoch, id = shopId.value
  controller?.abort(); controller = new AbortController()
  if (!Number.isSafeInteger(id) || id < 1) { detail.value = undefined; state.value = 'error'; errorMessage.value = '门店地址无效，请返回后重新选择。'; return }
  if (!keep) { detail.value = undefined; state.value = 'loading' }
  claimingId.value = undefined; pendingBlogId.value = undefined
  try {
    const result = await getShopDetail(id, controller.signal)
    if (current !== epoch || id !== shopId.value) return
    if (!result?.shop) throw new Error('门店不存在或已下线，请稍后重试。')
    detail.value = result; state.value = 'ready'
    if (visitedId !== id) { visitedId = id; void visit.record(id) }
  } catch (error) {
    if (current !== epoch || id !== shopId.value) return
    if (keep && detail.value) { ElMessage.warning('操作已完成，详情刷新失败，请稍后重试'); return }
    state.value = 'error'; errorMessage.value = error instanceof Error ? error.message : '门店详情加载失败，请稍后重试。'
  }
}
async function retryRecommendations() {
  const current = epoch, id = shopId.value
  try { const page = await getRecommendDishes(id,1,6); if (current === epoch && detail.value) {
    detail.value.recommendations = {status:page.records.length ? 'SUCCESS' : 'EMPTY',data:page}
    detail.value.recommendationStateAvailable = true
  } } catch { if (current === epoch) ElMessage.warning('操作已完成，推荐菜刷新失败，请重试') }
}
async function retryReviews() {
  const current = epoch, id = shopId.value
  try { const page = await getShopReviews(id,1); if (current === epoch && detail.value) {
    detail.value.reviews = {status:page.records.length ? 'SUCCESS' : 'EMPTY',data:{...page,records:page.records.slice(0,3)}}
    updateStats(page.averageScore ?? null,page.total)
  } } catch { if (current === epoch) ElMessage.warning('评价已发布，列表刷新失败，请重试') }
}
async function retryVouchers() {
  const current = epoch, id = shopId.value
  try { const page = await getShopVoucherPage(id,1,2); if (current === epoch && detail.value) detail.value.vouchers = { status: page.records.length ? 'SUCCESS' : 'EMPTY', data: page } }
  catch { if (current === epoch) ElMessage.error('优惠券加载失败，请重试') }
}
async function retryBlogs() {
  const current = epoch, id = shopId.value
  try { const page = await getShopBlogPage(id,1,4); if (current === epoch && detail.value) detail.value.blogs = { status: page.records.length ? 'SUCCESS' : 'EMPTY', data: page } }
  catch { if (current === epoch) ElMessage.error('探店笔记加载失败，请重试') }
}
async function retryRelated() {
  const current = epoch, id = shopId.value
  try { const section = await getRelatedShopPage(id,1,6); if (current === epoch && detail.value) detail.value.relatedShops = section }
  catch { if (current === epoch) ElMessage.error('周边推荐加载失败，请重试') }
}
watch([shopId, () => auth.isAuthenticated, () => auth.user?.id], () => { if (visitedId !== shopId.value) visit.reset(); void load() }, { immediate:true })
onBeforeUnmount(() => { epoch++; controller?.abort() })
defineExpose({ claim })
</script>
<style scoped>
.shop-detail-view{max-width:960px;margin:0 auto}.shop-hero{overflow:hidden;border:1px solid var(--color-border);border-radius:var(--radius-card);background:var(--color-surface);box-shadow:var(--shadow-card)}.gallery{display:flex;min-height:220px;overflow:auto;background:#fde9dc;scroll-snap-type:x mandatory}.gallery img{width:100%;min-width:100%;object-fit:cover;scroll-snap-align:start}.gallery>span{display:grid;place-items:center;width:100%}.shop-summary{padding:20px}.shop-summary h1{margin:0 0 10px;font-size:clamp(1.75rem,4vw,2.6rem)}.shop-summary p{color:var(--color-muted);font-size:.9rem}.shop-summary strong{color:#b56a09}.shop-summary strong span{margin-left:8px;color:var(--color-muted);font-size:.82rem}.shop-summary address{margin-top:14px;font-style:normal}.tag-title{margin-top:20px;font-size:1rem}.tags,.review-tags{display:flex;gap:8px;flex-wrap:wrap}.tags span,.review-tags span{padding:4px 10px;border-radius:16px;background:#fff0e8;color:#9b4526;font-size:.85rem}.review-tags{margin-top:24px}@media(min-width:768px){.shop-hero{display:grid;grid-template-columns:1.15fr 1fr}.gallery{min-height:300px}.shop-summary{align-content:center;display:grid}}
</style>
