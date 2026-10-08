import { dataOf, http } from './http'
import type { Shop, ShopType, Voucher } from '@/types/api'
export const getShopTypes = () => dataOf<ShopType[]>(http.get<ShopType[]>('/shop-type/list'))
export const getShopsByType = (params: {
  typeId: number
  current?: number
  sort?: 'distance' | 'comments' | 'score'
  longitude?: number
  latitude?: number
}) => dataOf<Shop[]>(http.get<Shop[]>('/shop/of/type', { params }))
export const searchShops = (name: string, current = 1) => dataOf<Shop[]>(http.get<Shop[]>('/shop/of/name', { params: { name, current } }))
export const getShop = (id: number) => dataOf<Shop>(http.get<Shop>(`/shop/${id}`))
export const getVouchers = (shopId: number) => dataOf<Voucher[]>(http.get<Voucher[]>(`/voucher/list/${shopId}`))
export const seckillVoucher = (id: number) => dataOf<number>(http.post<number>(`/voucher-order/seckill/${id}`))
