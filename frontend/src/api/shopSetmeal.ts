import { dataOf, http } from './http'
import type { ShopSetmeal } from '@/types/api'

/** 返回完整套餐与固定菜品，展开详情无需另发请求。 */
export const getShopSetmeals = (shopId: number, signal?: AbortSignal) =>
  dataOf<ShopSetmeal[]>(http.get<ShopSetmeal[]>(`/shop/${shopId}/setmeals`, { signal }))
