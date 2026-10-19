/** 浏览器用例共用商详聚合契约，避免继续依赖旧单店详情接口。 */
export const pageOf = (records: unknown[] = [], total = records.length, size = 10, current = 1) =>
  ({ records, total, size, current, pages: Math.ceil(total / size) })
export const section = (data: unknown) => ({ status: 'SUCCESS', data })
export function shopDetail(shop: Record<string, unknown>, extra: Record<string, unknown> = {}) {
  return { shop, vouchers: section(pageOf()), setmeals: section(pageOf()), recommendations: section(pageOf()),
    reviews: section(pageOf()), statistics: section({ totalReviews: 0, averageScore: null }),
    blogs: section(pageOf()), decisionTags: section([]), reviewTags: section([]),
    relatedShops: { status: 'UNAVAILABLE', data: null, message: '缺少位置信息，暂无法推荐周边店铺' },
    recommendationStateAvailable: true, ...extra }
}
