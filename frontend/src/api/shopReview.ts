import { dataOf, http } from './http'
import type { ShopReviewPage, ShopReviewTagOption } from '@/types/api'
export interface PublishShopReviewPayload {
  requestId: string; overallScore: number; tasteScore: number; environmentScore: number; serviceScore: number; content: string; tags: string[]
}
export const getShopReviews = (shopId: number, current = 1) => dataOf<ShopReviewPage>(http.get(`/shop/${shopId}/reviews`, { params: { current } }))
export const getReviewTags = () => dataOf<ShopReviewTagOption[]>(http.get('/shop/review-tags'))
export const publishShopReview = (shopId: number, payload: PublishShopReviewPayload) => dataOf<number>(http.post(`/shop/${shopId}/reviews`, payload))
