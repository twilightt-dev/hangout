<template>
  <div class="visit-statistic" data-test="visit-statistic" aria-live="polite">
    <View aria-hidden="true" />
    <span v-if="status === 'ready' && count !== null">{{ kind === 'blog' ? `累计浏览 ${count} 次` : `累计访客约 ${count} 位` }}</span>
    <span v-else>{{ status === 'unavailable' ? '暂无统计' : '统计加载中…' }}</span>
  </div>
</template>

<script setup lang="ts">
import { View } from '@element-plus/icons-vue'
import type { VisitKind } from '@/api/traffic'
import type { VisitState } from '@/composables/useVisitStatistic'
defineProps<{ kind: VisitKind; status: VisitState; count: number | null }>()
</script>

<style scoped>
.visit-statistic{display:flex;align-items:center;gap:6px;min-height:22px;margin:8px 0 12px;color:var(--color-muted);font-size:.82rem;overflow-wrap:anywhere}.visit-statistic svg{width:16px;height:16px;flex:none}
</style>
