<template>
  <article class="blog-card" :class="`blog-card--${variant}`">
    <button class="blog-card__open" type="button" data-test="blog-card-open" @click="$emit('open', blog)">
      <span v-if="imageUrl" class="blog-card__image-wrap"><img class="blog-card__image" :src="imageUrl" :alt="`${blog.title || '探店'}的图片`" loading="lazy"></span>
      <span v-else class="blog-card__placeholder" aria-label="暂无图片"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 19 9 13l3 3 3-4 5 7H4Zm0-14h16v10l-5-6-3 4-3-3-5 6V5Z" fill="currentColor"/></svg></span>
      <span class="blog-card__body"><strong>{{ blog.title || '未命名探店记录' }}</strong><span class="blog-card__author"><img v-if="blog.icon" :src="blog.icon" alt="" loading="lazy"><span v-else class="blog-card__avatar" aria-hidden="true">{{ authorInitial }}</span>{{ blog.name || '匿名探索者' }}</span></span>
    </button>
    <button class="blog-card__like" type="button" data-test="blog-like" :disabled="likeDisabled" :aria-pressed="!!blog.isLike" :aria-label="blog.isLike ? '取消点赞' : '点赞'" @click.stop="$emit('like', blog)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20.4 3.8 12A5.2 5.2 0 0 1 11 4.5L12 5.6l1-1.1a5.2 5.2 0 0 1 7.2 7.5L12 20.4Z" fill="none" stroke="currentColor" stroke-width="1.8"/></svg><span>{{ blog.liked || 0 }}</span></button>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Blog } from '@/types/api'
const props = withDefaults(defineProps<{ blog: Blog; variant?: 'grid' | 'row'; likeDisabled?: boolean }>(), { variant: 'grid', likeDisabled: false })
defineEmits<{ open: [blog: Blog]; like: [blog: Blog] }>()
const imageUrl = computed(() => props.blog.images?.split(',').map((item) => item.trim()).find(Boolean) || '')
const authorInitial = computed(() => (props.blog.name || '探').slice(0, 1))
</script>

<style scoped>
.blog-card{position:relative;min-width:0;overflow:hidden;background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--radius-card)}.blog-card__open{display:block;width:100%;padding:0;border:0;background:transparent;color:inherit;text-align:left;font:inherit;cursor:pointer}.blog-card__image-wrap,.blog-card__placeholder{display:block;aspect-ratio:1.18;background:#fde9dc;overflow:hidden}.blog-card__image{width:100%;height:100%;object-fit:cover}.blog-card__placeholder{display:grid;place-items:center;color:var(--color-primary)}.blog-card__placeholder svg{width:34px}.blog-card__body{display:grid;gap:9px;padding:12px 12px 14px;padding-right:58px}.blog-card__body strong{display:-webkit-box;overflow:hidden;font-size:.98rem;line-height:1.35;-webkit-line-clamp:2;-webkit-box-orient:vertical}.blog-card__author{display:flex;align-items:center;gap:6px;overflow:hidden;color:var(--color-muted);font-size:.8rem;white-space:nowrap;text-overflow:ellipsis}.blog-card__author img,.blog-card__avatar{width:22px;height:22px;border-radius:50%;object-fit:cover}.blog-card__avatar{display:grid;place-items:center;background:#f7c8af;color:#884625;font-size:.72rem}.blog-card__like{position:absolute;right:10px;bottom:11px;display:inline-flex;align-items:center;gap:4px;min-width:44px;min-height:44px;padding:6px;border:0;border-radius:999px;background:transparent;color:var(--color-muted);font:inherit;cursor:pointer}.blog-card__like:hover,.blog-card__like[aria-pressed="true"]{color:var(--color-primary);background:#fff0ea}.blog-card__like svg{width:18px}.blog-card--row{display:flex}.blog-card--row .blog-card__open{display:flex}.blog-card--row .blog-card__image-wrap,.blog-card--row .blog-card__placeholder{width:38%;flex:none;aspect-ratio:auto}.blog-card--row .blog-card__body{flex:1}
</style>
