<template>
  <section class="shop-detail-view"><PageState v-if="state !== 'ready'" :state="state" :message="errorMessage" @retry="load"/><template v-else-if="shop"><section class="shop-hero"><div class="gallery"><img v-for="(image, index) in images" :key="image" :src="image" :alt="`${shop.name || '门店'}图片 ${index + 1}`" width="720" height="480" loading="lazy"><span v-if="!images.length" class="gallery__empty">暂无门店图片</span></div><div class="shop-summary"><p>{{ shop.area || '附近' }}</p><h1>{{ shop.name || '未命名门店' }}</h1><strong>★ {{ scoreText }} <span>{{ shop.comments || 0 }} 条评价</span></strong><address>{{ shop.address || '地址待补充' }}</address><p>营业时间：{{ shop.openHours || '以门店公告为准' }}</p></div></section><section class="voucher-section" aria-labelledby="voucher-title"><div class="section-heading"><h2 id="voucher-title">店内优惠</h2><span>{{ vouchers.length ? '选择一张券去探店' : '暂无可领取优惠券' }}</span></div><div v-if="vouchers.length" class="voucher-list"><VoucherCard v-for="voucher in vouchers" :key="voucher.id" :voucher="voucher" :loading="claimingId === voucher.id" @claim="claim"/></div></section></template></section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getShop, getVouchers, seckillVoucher } from '@/api/shop'
import VoucherCard from '@/components/content/VoucherCard.vue'
import PageState from '@/components/common/PageState.vue'
import { useAuthStore } from '@/stores/auth'
import type { Shop, Voucher } from '@/types/api'
type ViewState = 'loading' | 'error' | 'empty' | 'ready'
const route = useRoute(); const router = useRouter(); const auth = useAuthStore(); const shop = ref<Shop>(); const vouchers = ref<Voucher[]>([]); const state = ref<ViewState>('loading'); const errorMessage = ref(''); const claimingId = ref<number>(); let requestEpoch = 0
const shopId = computed(() => Number(route.params.id)); const images = computed(() => shop.value?.images?.split(',').map((item) => item.trim()).filter(Boolean) || []); const scoreText = computed(() => Number.isFinite(shop.value?.score) ? (Number(shop.value?.score) / 10).toFixed(1) : '暂无评分')
function dateValue(value: string | undefined) { return value ? new Date(value.replace(' ', 'T')).getTime() : NaN }
async function load() { const currentEpoch = ++requestEpoch; shop.value = undefined; vouchers.value = []; claimingId.value = undefined; errorMessage.value = ''; if (!Number.isInteger(shopId.value) || shopId.value < 1) { state.value = 'error'; errorMessage.value = '门店地址无效，请返回后重新选择。'; return } const id = shopId.value; state.value = 'loading'; try { const [shopResult, voucherResult] = await Promise.all([getShop(id), getVouchers(id)]); if (currentEpoch !== requestEpoch) return; if (!shopResult) { state.value = 'error'; errorMessage.value = '门店不存在或已下线，请稍后重试。'; return } shop.value = shopResult; vouchers.value = voucherResult; state.value = 'ready' } catch { if (currentEpoch !== requestEpoch) return; state.value = 'error'; errorMessage.value = '门店详情加载失败，请稍后重试。' } }
async function claim(voucher: Voucher) {
  if (!auth.isAuthenticated) {
    await router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  const now = Date.now()
  const begins = dateValue(voucher.beginTime)
  const ends = dateValue(voucher.endTime)
  if (Number.isFinite(begins) && now < begins) { ElMessage.error('活动尚未开始'); return }
  if (Number.isFinite(ends) && now > ends) { ElMessage.error('活动已结束'); return }
  if (voucher.stock !== undefined && voucher.stock < 1) { ElMessage.error('优惠券已抢完'); return }
  if (voucher.id === undefined) { ElMessage.error('优惠券信息无效'); return }
  claimingId.value = voucher.id
  try {
    const orderId = await seckillVoucher(voucher.id)
    ElMessage.success(`领取成功，订单号：${orderId}`)
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '领取未成功，请稍后重试')
  } finally {
    claimingId.value = undefined
  }
}
void load()
watch(shopId, () => { void load() })
defineExpose({ claim })
</script>

<style scoped>
.shop-detail-view{max-width:960px;margin:0 auto}.shop-hero{overflow:hidden;border:1px solid var(--color-border);border-radius:var(--radius-card);background:var(--color-surface);box-shadow:var(--shadow-card)}.gallery{display:flex;min-height:220px;overflow:auto;background:#fde9dc;scroll-snap-type:x mandatory}.gallery img{width:100%;min-width:100%;object-fit:cover;scroll-snap-align:start}.gallery__empty{display:grid;place-items:center;width:100%;color:var(--color-primary)}.shop-summary{padding:20px}.shop-summary p{margin:0 0 6px;color:var(--color-muted);font-size:.88rem}.shop-summary h1{margin:0 0 10px;font-size:clamp(1.75rem,4vw,2.6rem)}.shop-summary strong{color:#b56a09}.shop-summary strong span{margin-left:8px;color:var(--color-muted);font-size:.82rem;font-weight:400}.shop-summary address{margin:14px 0 8px;color:var(--color-ink);font-style:normal}.voucher-section{margin-top:28px}.section-heading{display:flex;align-items:baseline;justify-content:space-between;gap:12px;margin-bottom:14px}.section-heading h2{margin:0;font-size:1.3rem}.section-heading span{color:var(--color-muted);font-size:.82rem}.voucher-list{display:grid;gap:12px}@media(min-width:768px){.shop-hero{display:grid;grid-template-columns:1.15fr 1fr}.gallery{min-height:300px}.shop-summary{align-content:center;display:grid}}
</style>
