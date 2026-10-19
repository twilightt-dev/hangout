<template>
  <section v-if="loading || error || meals.length" class="setmeals" aria-labelledby="setmeals-title" :aria-busy="loading">
    <div class="setmeals-heading"><h2 id="setmeals-title">店铺套餐</h2><span>好味一起分享</span></div>
    <p v-if="loading" role="status">套餐加载中…</p>
    <p v-else-if="error" role="alert">套餐加载失败，请稍后重试。 <button type="button" data-test="setmeal-retry" @click="load">重试</button></p>
    <div v-else class="setmeal-list">
      <details v-for="meal in meals" :key="meal.id" class="setmeal-card">
        <summary class="setmeal-summary">
          <img v-if="!failedImages.has(meal.id)" class="setmeal-cover" :src="meal.coverImage" :alt="meal.name" width="100" height="100" loading="lazy" @error="failedImages.add(meal.id)">
          <span v-else class="setmeal-cover setmeal-placeholder" role="img" :aria-label="`${meal.name}暂无图片`">暂无图片</span>
          <div class="setmeal-info">
            <h3>{{ meal.name }}</h3>
            <span class="setmeal-people">{{ meal.people }} 人套餐</span>
            <p v-if="meal.description" class="setmeal-description">{{ meal.description }}</p>
            <div class="setmeal-prices"><strong data-test="setmeal-price">¥{{ priceText(meal.price) }}</strong><del v-if="meal.originalPrice != null">¥{{ priceText(meal.originalPrice) }}</del></div>
            <span class="setmeal-expand">查看套餐内容 <span aria-hidden="true">⌄</span></span>
          </div>
        </summary>
        <div class="setmeal-details">
          <h4>套餐内容</h4>
          <ul data-test="setmeal-items"><li v-for="(item, index) in meal.items" :key="index">{{ item.name }} × {{ item.quantity }} {{ item.unit }}</li></ul>
          <h4>使用规则</h4>
          <p class="setmeal-rules" data-test="setmeal-rules">{{ meal.rules }}</p>
        </div>
      </details>
    </div>
    <button v-if="!listOnly && total > meals.length" type="button" @click="router.push(`/shops/${props.shopId}/lists/setmeals`)">查看更多套餐</button>
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getShopSetmeals } from '@/api/shopSetmeal'
import { available, type Section, type ShopPage } from '@/api/shopDetail'
import type { ShopSetmeal } from '@/types/api'
const props = defineProps<{ shopId: number; preview?: Section<ShopPage<ShopSetmeal>>; listOnly?: boolean }>()
const router = useRouter()
const meals = ref<ShopSetmeal[]>([])
const total = ref(0)
const loading = ref(true)
const error = ref(false)
const failedImages = ref(new Set<ShopSetmeal['id']>())
let requestEpoch = 0
let controller: AbortController | undefined
const priceText = (value: number) => (value / 100).toFixed(2)
async function load() {
  const epoch = ++requestEpoch
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true; error.value = false; meals.value = []; failedImages.value.clear()
  try {
    const result = await getShopSetmeals(props.shopId, request.signal)
    if (epoch === requestEpoch && !request.signal.aborted) { meals.value = result.records; total.value = result.total }
  } catch {
    if (epoch === requestEpoch && !request.signal.aborted) error.value = true
  } finally {
    if (epoch === requestEpoch && !request.signal.aborted) loading.value = false
  }
}
function initial() {
  requestEpoch++; controller?.abort(); loading.value = false
  meals.value = props.preview?.data?.records || []; total.value = props.preview?.data?.total || 0
  error.value = !available(props.preview)
}
watch([() => props.shopId, () => props.preview], () => {
  if (props.preview) initial(); else void load()
}, { immediate: true })
onBeforeUnmount(() => { requestEpoch++; controller?.abort() })
</script>
<style scoped>
.setmeals{margin-top:28px}.setmeals-heading{display:flex;align-items:baseline;justify-content:space-between;gap:12px;margin-bottom:14px}.setmeals-heading h2{margin:0;font-size:1.3rem}.setmeals-heading>span,.setmeal-people,.setmeal-description,.setmeal-expand{color:var(--color-muted);font-size:.85rem}.setmeal-list{display:grid;gap:12px}.setmeal-card{overflow:hidden;border:1px solid var(--color-border);border-radius:var(--radius-card);background:var(--color-surface)}.setmeal-summary{display:flex;gap:16px;padding:16px;cursor:pointer;list-style:none}.setmeal-summary::-webkit-details-marker{display:none}.setmeal-summary:focus-visible{outline:3px solid var(--color-focus);outline-offset:-3px}.setmeal-cover{flex:none;width:100px;height:100px;object-fit:cover;border-radius:12px;background:#fde9dc}.setmeal-info{min-width:0;flex:1}.setmeal-info h3{margin:0 0 6px;font-size:1rem;overflow-wrap:anywhere}.setmeal-description{margin:8px 0;overflow-wrap:anywhere}.setmeal-prices{display:flex;gap:10px;align-items:baseline;margin-top:10px}.setmeal-prices strong{color:var(--color-primary);font-size:1.4rem}.setmeal-prices del{color:var(--color-muted);font-size:.8rem}.setmeal-expand{display:block;margin-top:8px}.setmeal-card[open] .setmeal-expand span{display:inline-block;transform:rotate(180deg)}.setmeal-details{padding:0 20px 20px;border-top:1px solid var(--color-border)}.setmeal-details h4{margin:16px 0 10px}.setmeal-details ul{margin:0;padding-left:20px;line-height:1.9;overflow-wrap:anywhere}.setmeal-rules{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7;color:var(--color-muted)}@media(max-width:420px){.setmeal-summary{gap:12px;padding:12px}.setmeal-cover{width:80px;height:80px}.setmeals-heading>span{display:none}}
</style>
<style scoped>.setmeal-placeholder{display:grid;place-items:center;color:var(--color-muted);font-size:.8rem}</style>
