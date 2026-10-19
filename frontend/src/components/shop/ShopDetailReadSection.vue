<template>
  <section class="read-section" :aria-labelledby="`read-${kind}-title`">
    <header><h2 :id="`read-${kind}-title`">{{ title }}</h2>
      <button v-if="!listOnly && section?.data && section.data.total > section.data.records.length" type="button" @click="router.push(`/shops/${shopId}/lists/${kind}`)">查看更多</button>
    </header>
    <p v-if="section && !available(section)" role="alert">{{ section.message || '加载失败，请稍后重试' }}
      <button v-if="section.status !== 'UNAVAILABLE'" type="button" @click="$emit('retry')">重试</button>
    </p>
    <p v-else-if="!section?.data?.records.length" class="empty">{{ emptyText }}</p>
    <div v-else class="read-grid" :class="{ 'read-grid--blogs': kind === 'blogs' }">
      <VoucherCard v-for="voucher in vouchers?.data?.records" :key="voucher.id" :voucher="voucher" :loading="claimingId === voucher.id" @claim="$emit('claim', voucher)"/>
      <BlogCard v-for="blog in blogs?.data?.records" :key="blog.id" :blog="blog" :like-disabled="blog.isLike == null || pendingBlogId === blog.id" @open="router.push(`/blogs/${blog.id}`)" @like="$emit('like', blog)"/>
      <article v-for="shop in related?.data?.records" :key="shop.id" class="nearby-card">
        <RouterLink :to="`/shops/${shop.id}`">{{ shop.name }}</RouterLink><p>{{ shop.area || '周边同类店' }}</p>
        <p>距该店 {{ formatDistance(shop.distance) }}<span v-if="shop.avgPrice != null"> · ¥{{ shop.avgPrice }}/人</span></p>
      </article>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { Voucher, Blog } from '@/types/api'
import { available, type Section, type ShopPage, type RelatedShop } from '@/api/shopDetail'
import { formatDistance } from '@/utils/format'
import VoucherCard from '@/components/content/VoucherCard.vue'
import BlogCard from '@/components/content/BlogCard.vue'
const props = defineProps<{ shopId: number; vouchers?: Section<ShopPage<Voucher>>; blogs?: Section<ShopPage<Blog>>; related?: Section<ShopPage<RelatedShop>>; listOnly?: boolean; claimingId?: number; pendingBlogId?: number }>()
defineEmits<{ retry: []; claim: [voucher: Voucher]; like: [blog: Blog] }>()
const router = useRouter()
const section = computed(() => props.vouchers || props.blogs || props.related)
const kind = computed(() => props.vouchers ? 'vouchers' : props.blogs ? 'blogs' : 'related-shops')
const title = computed(() => props.vouchers ? '店内优惠' : props.blogs ? '达人探店' : '周边推荐')
const emptyText = computed(() => props.vouchers ? '暂无上架优惠券' : props.blogs ? '暂无探店笔记' : '附近暂无同类型店铺')
</script>
<style scoped>
.read-section{margin-top:28px;padding:20px;border:1px solid var(--color-border);border-radius:var(--radius-card);background:var(--color-surface)}header{display:flex;align-items:center;justify-content:space-between;gap:12px}h2{font-size:1.3rem;margin:0 0 16px}.read-grid{display:grid;gap:12px}.read-grid--blogs{grid-template-columns:repeat(2,minmax(0,1fr))}.nearby-card{padding:14px;border:1px solid var(--color-border);border-radius:var(--radius-control)}.nearby-card a{font-weight:700;color:var(--color-ink)}.nearby-card p,.empty{color:var(--color-muted)}button{min-height:40px;padding:8px 12px;cursor:pointer}[role=alert]{color:#a52d25}@media(min-width:768px){.read-grid--blogs{grid-template-columns:repeat(4,minmax(0,1fr))}}
</style>
