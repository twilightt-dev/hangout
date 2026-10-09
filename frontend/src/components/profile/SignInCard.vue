<template>
  <section class="sign-in" aria-labelledby="sign-title" :aria-busy="loading || signing">
    <div class="sign-heading">
      <div><h2 id="sign-title">每日签到</h2><p>{{ monthLabel }}</p></div>
      <button type="button" class="sign-button" data-test="sign-button" :disabled="signing || todaySigned" @click="submit">
        <Check v-if="todaySigned" aria-hidden="true" /><Calendar v-else aria-hidden="true" />
        {{ signing ? '签到中...' : todaySigned ? '今日已签到' : '签到' }}
      </button>
    </div>
    <p v-if="notice" class="sign-notice" role="status">{{ notice }}</p>
    <p v-if="signError" class="sign-error" role="alert">{{ signError }}</p>
    <p v-if="loading" class="sign-loading" role="status">正在加载签到统计...</p>
    <div v-else-if="statsError" class="sign-error-row">
      <p class="sign-error" role="alert">{{ statsError }}</p>
      <button type="button" class="stats-retry" data-test="stats-retry" @click="loadStats"><Refresh aria-hidden="true" />重试</button>
    </div>
    <dl v-else-if="stats" class="sign-stats">
      <div><dt>本月累计</dt><dd data-test="monthly-days">{{ stats.monthlyDays }}<span>天</span></dd></div>
      <div><dt>本月连续</dt><dd data-test="continuous-days">{{ stats.continuousDays }}<span>天</span></dd></div>
      <div class="sign-comparison"><dt>上月全月 {{ stats.previousMonthDays }} 天</dt><dd data-test="month-comparison">{{ comparison }}</dd></div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Calendar, Check, Refresh } from '@element-plus/icons-vue'
import { getSignStats, signToday } from '@/api/user'
import type { SignStats } from '@/types/api'

const DAY_MS = 86_400_000
const SHANGHAI_OFFSET_MS = 8 * 3_600_000
function businessDate() { return new Date(Date.now() + SHANGHAI_OFFSET_MS).toISOString().slice(0, 10) }
const date = ref(businessDate())
const stats = ref<SignStats>()
const confirmedDate = ref('')
const loading = ref(false)
const signing = ref(false)
const statsError = ref('')
const signError = ref('')
const notice = ref('')
let disposed = false
let requestVersion = 0
let midnightTimer: ReturnType<typeof setTimeout> | undefined
const todaySigned = computed(() => confirmedDate.value === date.value || (stats.value?.date === date.value && stats.value.todaySigned))
const monthLabel = computed(() => `${date.value.slice(0, 4)} 年 ${Number(date.value.slice(5, 7))} 月`)
const comparison = computed(() => {
  const difference = stats.value?.monthDifference ?? 0
  return difference === 0 ? '与上月全月持平' : `比上月全月${difference > 0 ? '多' : '少'} ${Math.abs(difference)} 天`
})

function updateDate() {
  const current = businessDate()
  if (current === date.value) return
  date.value = current
  stats.value = undefined
  confirmedDate.value = ''
  notice.value = ''
  signError.value = ''
}

async function loadStats() {
  if (disposed) return
  updateDate()
  const version = ++requestVersion
  loading.value = true
  statsError.value = ''
  try {
    const result = await getSignStats()
    if (disposed || version !== requestVersion) return
    if (!result || !result.date || !Number.isInteger(result.monthlyDays)) throw new Error('Invalid sign-in statistics')
    updateDate()
    if (result.date !== date.value) throw new Error('Outdated sign-in statistics')
    stats.value = result
    if (result.todaySigned) confirmedDate.value = result.date
  } catch {
    if (!disposed && version === requestVersion) {
      stats.value = undefined
      statsError.value = '统计加载失败，请重试。'
    }
  } finally {
    if (!disposed && version === requestVersion) loading.value = false
  }
}

async function submit() {
  if (disposed || signing.value || todaySigned.value) return
  const requestDate = date.value
  signing.value = true
  signError.value = ''
  notice.value = ''
  // Ignore a statistics request started before this write.
  ++requestVersion
  loading.value = false
  try {
    const result = await signToday()
    if (disposed) return
    if (!result || !result.date) throw new Error('Missing sign-in acknowledgement')
    updateDate()
    confirmedDate.value = result.date
    notice.value = result.alreadySigned ? '今日已签到' : '签到成功'
    await loadStats()
  } catch {
    if (!disposed) signError.value = '签到未确认，请重试。'
  } finally {
    if (!disposed) {
      signing.value = false
      if (date.value !== businessDate() || (requestDate !== date.value && !stats.value && !statsError.value)) void loadStats()
    }
  }
}

function refreshWhenVisible() {
  if (!document.hidden && !signing.value) void loadStats()
}
function scheduleMidnight() {
  const shiftedNow = Date.now() + SHANGHAI_OFFSET_MS
  midnightTimer = setTimeout(() => {
    updateDate()
    if (!signing.value) void loadStats()
    scheduleMidnight()
  }, DAY_MS - shiftedNow % DAY_MS + 30)
}
onMounted(() => {
  void loadStats()
  scheduleMidnight()
  document.addEventListener('visibilitychange', refreshWhenVisible)
  window.addEventListener('focus', refreshWhenVisible)
})
onBeforeUnmount(() => {
  disposed = true
  ++requestVersion
  clearTimeout(midnightTimer)
  document.removeEventListener('visibilitychange', refreshWhenVisible)
  window.removeEventListener('focus', refreshWhenVisible)
})
</script>

<style scoped>
.sign-in{margin-top:20px;padding:18px 0;border-top:1px solid var(--color-border);border-bottom:1px solid var(--color-border)}
.sign-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.sign-heading h2{margin:0;font-size:1.05rem}.sign-heading p{margin:4px 0 0;color:var(--color-muted);font-size:.84rem}
.sign-button,.stats-retry{display:inline-flex;align-items:center;justify-content:center;gap:6px;min-height:44px;border:1px solid var(--color-primary);border-radius:var(--radius-control);padding:8px 14px;background:var(--color-primary);color:#fff;font:inherit;font-weight:600;cursor:pointer}.sign-button{min-width:130px}.sign-button svg,.stats-retry svg{width:18px;height:18px;flex-shrink:0}.sign-button:disabled{border-color:var(--color-border);background:var(--color-surface);color:var(--color-muted);cursor:default}.stats-retry{background:var(--color-surface);color:var(--color-primary);flex-shrink:0}
.sign-stats{display:grid;grid-template-columns:1fr 1fr 1.4fr;gap:16px;margin:20px 0 0}.sign-stats>div{min-width:0}.sign-stats dt{font-size:.8rem;color:var(--color-muted)}.sign-stats dd{margin:6px 0 0;font-size:1.5rem;font-weight:700}.sign-stats dd span{margin-left:4px;font-size:.8rem;font-weight:400;color:var(--color-muted)}.sign-comparison dd{font-size:.9rem;line-height:1.7;color:var(--color-ink)}
.sign-notice{color:#217346;font-size:.85rem}.sign-error,.sign-loading{font-size:.85rem;line-height:1.6}.sign-error{color:#b42318}.sign-loading{color:var(--color-muted)}.sign-error-row{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px}
@media(max-width:440px){.sign-stats{grid-template-columns:repeat(2,minmax(0,1fr))}.sign-comparison{grid-column:1/-1}.sign-comparison dd{margin-top:4px}}
</style>
