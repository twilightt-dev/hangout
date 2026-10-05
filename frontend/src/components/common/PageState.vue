<template>
  <section class="page-state" :class="`page-state--${state}`" :aria-live="state === 'loading' ? 'polite' : 'assertive'">
    <div v-if="state === 'loading'" class="state-loading" role="status"><span v-for="index in 3" :key="index"></span><span class="sr-only">正在加载</span></div>
    <template v-else-if="state === 'error'">
      <p>{{ message || '加载失败，请检查网络后重试。' }}</p>
      <button type="button" class="state-retry" @click="$emit('retry')">重新加载</button>
    </template>
    <p v-else>{{ message || '还没有内容，去城市里发现一点新鲜事吧。' }}</p>
  </section>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ state: 'loading' | 'error' | 'empty'; message?: string }>(), { message: '' })
defineEmits<{ retry: [] }>()
</script>

<style scoped>
.page-state{margin:24px 0;padding:28px 20px;border:1px dashed var(--color-border);border-radius:var(--radius-card);text-align:center;color:var(--color-muted)}.state-loading{display:grid;gap:10px}.state-loading>span:not(.sr-only){display:block;height:14px;border-radius:999px;background:linear-gradient(90deg,#f5e6dc,#fff7f2,#f5e6dc);background-size:200% 100%;animation:loading 1.4s linear infinite}.state-loading>span:nth-child(2){width:82%}.state-loading>span:nth-child(3){width:58%}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}@keyframes loading{to{background-position:-200% 0}}
.state-retry{min-height:44px;padding:8px 16px;border:0;border-radius:var(--radius-control);background:var(--color-primary);color:#fff;font:inherit;font-weight:700;cursor:pointer}
</style>
