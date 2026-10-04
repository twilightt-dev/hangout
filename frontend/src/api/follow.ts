import { dataOf, http } from './http'
import type { CommonFollowVO } from '@/types/api'
export const getFollowStatus = (userId: number) => dataOf<boolean>(http.get<boolean>(`/follow/or/not/${userId}`))
export const setFollow = (userId: number, toFollow: boolean) => dataOf<void>(http.put(`/follow/${userId}/${toFollow}`))
export const getCommonFollows = (userId: number) => dataOf<CommonFollowVO[]>(http.get<CommonFollowVO[]>(`/follow/common/${userId}`))
