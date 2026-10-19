import { onScopeDispose, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { seckillVoucher } from '@/api/shop'
import { likeBlog } from '@/api/blog'
import { useAuthStore } from '@/stores/auth'
import type { Voucher, Blog } from '@/types/api'

/** 首屏和独立列表共用写操作；身份或店铺变化后不接收旧操作的界面结果。 */
export function useShopActions(shopId: Ref<number>, refreshVouchers: () => Promise<void>, refreshBlogs: () => Promise<void>) {
  const auth = useAuthStore(), route = useRoute(), router = useRouter()
  const claimingId = ref<number>(), pendingBlogId = ref<number>()
  let actionEpoch = 0
  watch([shopId, () => auth.isAuthenticated, () => auth.user?.id], () => {
    actionEpoch++; claimingId.value = undefined; pendingBlogId.value = undefined
  })
function requireLogin() {
  if (auth.isAuthenticated) return true
  void router.push({ name:'login', query:{ redirect:route.fullPath } }); return false
}
function dateValue(value?: string) { return value ? new Date(value.replace(' ','T')).getTime() : NaN }
async function claim(voucher: Voucher) {
  if (!requireLogin() || claimingId.value != null) return
  const now = Date.now()
  if (dateValue(voucher.beginTime) > now) { ElMessage.error('活动尚未开始'); return }
  if (dateValue(voucher.endTime) <= now) { ElMessage.error('活动已结束'); return }
  if (voucher.stock != null && voucher.stock < 1) { ElMessage.error('优惠券已抢完'); return }
  if (voucher.type !== 1) { ElMessage.info('普通优惠券请到店咨询使用，线上暂不支持购买'); return }
  if (!voucher.id) { ElMessage.error('优惠券信息无效'); return }
  const current = actionEpoch; claimingId.value = voucher.id
  try {
    const orderId = await seckillVoucher(voucher.id)
    if (current !== actionEpoch) return
    ElMessage.success(`领取成功，订单号：${orderId}`); await refreshVouchers()
  } catch (error) { if (current === actionEpoch) ElMessage.error(error instanceof Error && error.message ? error.message : '领取未成功，请稍后重试') }
  finally { if (current === actionEpoch) claimingId.value = undefined }
}
async function like(blog: Blog) {
  if (!requireLogin() || blog.id == null || blog.isLike == null || pendingBlogId.value != null) return
  const current = actionEpoch; pendingBlogId.value = blog.id
  try { await likeBlog(blog.id); if (current === actionEpoch) await refreshBlogs() }
  catch { if (current === actionEpoch) ElMessage.error('点赞结果未确认，请刷新后查看'); if (current === actionEpoch) await refreshBlogs() }
  finally { if (current === actionEpoch) pendingBlogId.value = undefined }
}

  onScopeDispose(() => { actionEpoch++ })
  return { claimingId, pendingBlogId, claim, like }
}
