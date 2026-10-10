import { dataOf, http } from './http'

export type VisitKind = 'blog' | 'shop'
export type VisitPayload = { visitorId: string; eventId: string }
export type VisitResponse = { count: number }
export type VisitRequest = (payload: VisitPayload, config?: { signal?: AbortSignal }) => Promise<VisitResponse>

export const sendVisit = (kind: VisitKind, id: number, payload: VisitPayload, config?: { signal?: AbortSignal }) =>
  dataOf<VisitResponse>(http.post<VisitResponse>(`/${kind}/${id}/visit`, payload, config))

const visitorStorageKey = 'visit:visitorId'
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function createUuid(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // HTTP 开发环境可能没有 randomUUID，仍使用浏览器安全随机数生成访客标识。
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function getVisitorId(): string | null {
  try {
    const stored = localStorage.getItem(visitorStorageKey)
    if (stored && uuidPattern.test(stored)) return stored
    localStorage.setItem(visitorStorageKey, createUuid())
    const persisted = localStorage.getItem(visitorStorageKey)
    return persisted && uuidPattern.test(persisted) ? persisted : null
  } catch {
    return null
  }
}

function pause(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const cancel = () => {
      window.clearTimeout(timer)
      reject(new DOMException('访问上报已取消', 'AbortError'))
    }
    const timer = window.setTimeout(() => {
      signal?.removeEventListener('abort', cancel)
      resolve()
    }, milliseconds)
    signal?.addEventListener('abort', cancel, { once: true })
    if (signal?.aborted) cancel()
  })
}

export async function reportVisit(request: VisitRequest, options: { signal?: AbortSignal; maxAttempts?: number; backoffMs?: number } = {}): Promise<VisitResponse> {
  const visitorId = getVisitorId()
  if (!visitorId) throw new Error('无法保存访客标识')
  const payload = { visitorId, eventId: createUuid() }
  const maxAttempts = options.maxAttempts ?? 3
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    if (options.signal?.aborted) throw new DOMException('访问上报已取消', 'AbortError')
    try {
      return await request(payload, { signal: options.signal })
    } catch (error) {
      const code = typeof error === 'object' && error && 'code' in error ? (error as { code?: number }).code : undefined
      if (options.signal?.aborted || (code !== undefined && code < 500) || attempt + 1 === maxAttempts) throw error
      await pause(options.backoffMs ?? (attempt === 0 ? 500 : 1500), options.signal)
    }
  }
  throw new Error('访问上报失败')
}
