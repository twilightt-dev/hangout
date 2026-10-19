import { dataOf, http } from './http'

export interface RecommendDish {
  id: number
  shopId: number
  name: string
  coverImage: string
  recommendCount: number
  recommended: boolean
  createTime: string
}

export interface RecommendDishPage {
  records: RecommendDish[]
  total: number
  current: number
  size: number
  pages: number
}

export interface PublishRecommendDishPayload {
  requestId: string
  name: string
  coverImage: string
}

export const getRecommendDishes = (shopId: number, current = 1, size = 12, signal?: AbortSignal) =>
  dataOf<RecommendDishPage>(http.get(`/shop/${shopId}/recommend-dishes`, { params: { current, size }, signal }))

export const publishRecommendDish = (shopId: number, payload: PublishRecommendDishPayload) =>
  dataOf<RecommendDish>(http.post(`/shop/${shopId}/recommend-dishes`, payload))

/** 明确设置为已推荐；重试不会反转状态或重复增加人数。 */
export const recommendDish = (shopId: number, dishId: number) =>
  dataOf<RecommendDish>(http.put(`/shop/${shopId}/recommend-dishes/${dishId}/recommendation`))
