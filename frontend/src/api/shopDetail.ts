import { dataOf, http } from './http'
import type { Shop, Voucher, Blog, ShopSetmeal, ShopReview, ShopReviewPage } from '@/types/api'
import type { RecommendDish, RecommendDishPage } from './recommendDish'
export interface ShopPage<T> { records: T[]; total: number; current: number; size: number; pages: number }
export interface Section<T> { status: 'SUCCESS' | 'EMPTY' | 'PARTIAL' | 'FAILED' | 'TIMED_OUT' | 'UNAVAILABLE'; data: T | null; message?: string | null }
export interface DecisionTag { code: string; name: string; userCount: number }
export interface RelatedShop { id: number; name: string; area?: string; avgPrice?: number; score?: number; distance: number; recommendationReason?: string }
export interface ReviewStatistics { totalReviews: number; averageScore: number | null }
export interface ShopDetail {
  shop: Shop
  vouchers: Section<ShopPage<Voucher>>
  setmeals: Section<ShopPage<ShopSetmeal>>
  recommendations: Section<RecommendDishPage>
  reviews: Section<ShopPage<ShopReview>>
  statistics: Section<ReviewStatistics>
  blogs: Section<ShopPage<Blog>>
  decisionTags: Section<DecisionTag[]>
  reviewTags: Section<DecisionTag[]>
  relatedShops: Section<ShopPage<RelatedShop>>
  recommendationStateAvailable: boolean
}
export const available = (section?: Section<unknown>) => !!section && ['SUCCESS', 'EMPTY', 'PARTIAL'].includes(section.status)
export const getShopDetail = (shopId: number, signal?: AbortSignal) => dataOf<ShopDetail>(http.get(`/shop/${shopId}/detail`, { signal }))
export const getShopVoucherPage = (shopId: number, current = 1, size = 10, signal?: AbortSignal) =>
  dataOf<ShopPage<Voucher>>(http.get(`/shop/${shopId}/vouchers`, { params: { current, size }, signal }))
export const getShopBlogPage = (shopId: number, current = 1, size = 10, signal?: AbortSignal) =>
  dataOf<ShopPage<Blog>>(http.get(`/shop/${shopId}/blogs`, { params: { current, size }, signal }))
export const getRelatedShopPage = (shopId: number, current = 1, size = 10, signal?: AbortSignal) =>
  dataOf<Section<ShopPage<RelatedShop>>>(http.get(`/shop/${shopId}/related-shops`, { params: { current, size }, signal }))
export function reviewPreview(detail: ShopDetail): Section<ShopReviewPage> & { statisticsAvailable: boolean } {
  return { ...detail.reviews, statisticsAvailable: available(detail.statistics), data: detail.reviews.data ? { ...detail.reviews.data,
    averageScore: detail.statistics.data?.averageScore ?? null } : null }
}
