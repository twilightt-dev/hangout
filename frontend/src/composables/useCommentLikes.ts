import { ref, watch, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBlogComments, toggleBlogCommentLike } from '@/api/blog'
import type { Comment } from '@/types/api'

/** 评论切换失败只读状态；每条评论单独防连点，旧页面和旧身份响应都不再生效。 */
export function useCommentLikes(
  comments: Ref<Comment[]>,
  context: { blogId: () => number; epoch: () => number; authenticated: () => boolean; userId: () => number | undefined; login: () => unknown; reload: () => Promise<void> },
) {
  const busy = ref(new Set<number>())
  const pages = new Map<number, number>()
  const confirmed = new Map<number, { liked: number; isLike: boolean; revision: number }>()
  let identityEpoch = 0
  let mutationRevision = 0
  const identityKey = () => `${context.authenticated()}:${context.userId() ?? ''}`
  const ticket = () => ({ blog: context.blogId(), page: context.epoch(), identity: identityEpoch, key: identityKey(), revision: mutationRevision })
  type Ticket = ReturnType<typeof ticket>
  const current = (action: Ticket) => context.blogId() === action.blog && context.epoch() === action.page && identityEpoch === action.identity && identityKey() === action.key
  function reset() { busy.value = new Set(); pages.clear(); confirmed.clear() }
  function merge(records: Comment[], page: number, started: Ticket) {
    return records.map(comment => {
      if (comment.id === undefined) return comment
      pages.set(comment.id, page)
      const local = confirmed.get(comment.id)
      // 操作成功的 Redis 结果在本次详情浏览中优先于可能滞后的计数投影。
      if (!local) return comment
      if (comment.likeAvailable !== true || comment.isLike == null) return { ...comment, liked: local.liked }
      if (started.revision < local.revision) return { ...comment, liked: local.liked, isLike: local.isLike }
      if (comment.isLike !== local.isLike || comment.liked === local.liked) { confirmed.delete(comment.id); return comment }
      return { ...comment, liked: local.liked }
    })
  }
  function unknown(id: number) {
    comments.value = comments.value.map(comment => comment.id === id
      ? { ...comment, isLike: context.authenticated() ? null : false, likeAvailable: false } : comment)
  }
  async function readState(id: number, action: Ticket) {
    const page = await getBlogComments(action.blog, pages.get(id) ?? 1)
    if (!current(action)) return
    const found = page.records?.find(comment => comment.id === id)
    if (!found) { unknown(id); await context.reload(); return }
    comments.value = comments.value.map(comment => comment.id === id ? { ...comment, ...found } : comment)
  }
  async function retry(comment: Comment) {
    const id = comment.id
    if (id === undefined || busy.value.has(id)) return
    const action = ticket(); busy.value.add(id)
    try { await readState(id, action) }
    catch { if (current(action)) unknown(id) }
    finally { if (current(action)) busy.value.delete(id) }
  }
  async function toggle(comment: Comment) {
    if (!context.authenticated()) { context.login(); return }
    const id = comment.id
    if (id === undefined || busy.value.has(id) || comment.likeAvailable !== true || comment.isLike == null) return
    const action = ticket(); busy.value.add(id)
    try {
      const result = await toggleBlogCommentLike(action.blog, id)
      if (!current(action)) return
      confirmed.set(id, { ...result, revision: ++mutationRevision })
      comments.value = comments.value.map(row => row.id === id ? { ...row, ...result, likeAvailable: true } : row)
    } catch {
      if (!current(action)) return
      confirmed.delete(id); unknown(id)
      ElMessage.error('点赞结果未确认，正在重新读取状态')
      try { await readState(id, action) }
      catch { if (current(action)) unknown(id) }
    } finally { if (current(action)) busy.value.delete(id) }
  }
  watch(identityKey, () => {
    identityEpoch++; busy.value = new Set(); confirmed.clear()
    comments.value = comments.value.map(comment => ({ ...comment, isLike: context.authenticated() ? null : false, likeAvailable: false }))
    // 保留已加载内容，由每条评论的读取重试重新确认当前身份。
  }, { flush: 'sync' })
  return { busy, merge, reset, toggle, retry, ticket, current }
}
