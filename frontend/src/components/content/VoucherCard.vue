<template>
  <article class="voucher-card" :class="{ 'voucher-card--seckill': isSeckill, 'voucher-card--disabled': disabled }">
    <div class="voucher-card__value"><small>¥</small>{{ formatPrice(voucher.payValue) }}<span>价值 ¥{{ formatPrice(voucher.actualValue) }}</span></div>
    <div class="voucher-card__body"><strong>{{ voucher.title || '到店优惠券' }}</strong><span v-if="voucher.subTitle">{{ voucher.subTitle }}</span><span>{{ timeText }}</span><span v-if="isSeckill">剩余 {{ voucher.stock ?? 0 }} 张</span></div>
    <button type="button" class="voucher-card__claim" :disabled="disabled" @click="$emit('claim', voucher)">{{ loading ? '领取中…' : actionText }}</button>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Voucher } from '@/types/api'
import { formatPrice } from '@/utils/format'
const props = withDefaults(defineProps<{ voucher: Voucher; loading?: boolean }>(), { loading: false })
defineEmits<{ claim: [voucher: Voucher] }>()
const isSeckill = computed(() => props.voucher.type === 1)
const timeText = computed(() => props.voucher.beginTime && props.voucher.endTime ? `有效期：${props.voucher.beginTime} 至 ${props.voucher.endTime}` : props.voucher.rules || '使用规则以门店说明为准')
const actionText = computed(() => {
  if (props.voucher.availability === 'UPCOMING') return '尚未开始'
  if (props.voucher.availability === 'SOLD_OUT' || (isSeckill.value && (props.voucher.stock ?? 0) < 1)) return '已售罄'
  return isSeckill.value ? '立即秒杀' : '到店咨询'
})
const disabled = computed(() => props.loading || ['UPCOMING', 'SOLD_OUT'].includes(props.voucher.availability || '') || (isSeckill.value && (props.voucher.stock ?? 0) < 1))
</script>

<style scoped>
.voucher-card{display:grid;grid-template-columns:auto 1fr;gap:8px 14px;align-items:center;padding:14px;border:1px dashed #e4aa91;border-radius:var(--radius-card);background:#fff9f4}.voucher-card--seckill{border-style:solid;border-color:#f1b8a4}.voucher-card--disabled{opacity:.6}.voucher-card__value{color:var(--color-primary);font-size:1.45rem;font-weight:800;white-space:nowrap}.voucher-card__value small{font-size:.75rem}.voucher-card__value span{display:block;color:var(--color-muted);font-size:.68rem;font-weight:400;text-decoration:line-through}.voucher-card__body{display:grid;gap:4px;min-width:0;color:var(--color-muted);font-size:.76rem}.voucher-card__body strong{overflow:hidden;color:var(--color-ink);font-size:.95rem;white-space:nowrap;text-overflow:ellipsis}.voucher-card__claim{grid-column:1/-1;min-height:44px;border:0;border-radius:var(--radius-control);background:var(--color-primary);color:#fff;font:inherit;font-weight:700;cursor:pointer}.voucher-card__claim:disabled{cursor:not-allowed;background:#b7aaa4}.voucher-card__claim:focus-visible{outline:3px solid var(--color-focus);outline-offset:2px}@media(min-width:540px){.voucher-card{grid-template-columns:auto 1fr auto}.voucher-card__claim{grid-column:auto;min-width:92px}}
</style>
