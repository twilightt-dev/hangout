import { nextTick, onBeforeUnmount, ref } from 'vue'
import { reportVisit, sendVisit, type VisitKind } from '@/api/traffic'

export type VisitState = 'loading' | 'ready' | 'unavailable'

export function useVisitStatistic(kind: VisitKind) {
  const status = ref<VisitState>('loading')
  const count = ref<number | null>(null)
  let controller: AbortController | undefined
  let epoch = 0
  let disposed = false

  function reset() {
    epoch += 1
    controller?.abort()
    controller = undefined
    count.value = null
    status.value = 'loading'
  }

  async function record(id: number) {
    const currentEpoch = epoch
    // 等待详情内容写入 DOM，避免未展示的查询被当作访问。
    await nextTick()
    if (disposed || currentEpoch !== epoch) return
    const activeController = new AbortController()
    controller = activeController
    const isCurrent = () => !disposed && currentEpoch === epoch && !activeController.signal.aborted
    try {
      const result = await reportVisit((payload, config) => sendVisit(kind, id, payload, config), { signal: activeController.signal })
      if (!isCurrent()) return
      if (!Number.isSafeInteger(result.count) || result.count < 0) throw new Error('统计响应无效')
      count.value = result.count
      status.value = 'ready'
    } catch {
      if (isCurrent()) status.value = 'unavailable'
    }
  }

  onBeforeUnmount(() => { disposed = true; reset() })
  return { status, count, record, reset }
}
