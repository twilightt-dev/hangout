import { dataOf, http } from './http'
import type { ShopSetmeal } from '@/types/api'
import type { ShopPage } from './shopDetail'
/** 每个套餐保留完整菜品和规则，列表改为独立分页。 */
export const getShopSetmeals = (shopId: number, signal?: AbortSignal, current = 1, size = 10) =>
  dataOf<ShopPage<ShopSetmeal>>(http.get(`/shop/${shopId}/setmeals`, { params: { current, size }, signal }))
