import { dataOf, http } from './http'
import type { User } from '@/types/api'
export const getFollowStatus = (userId: number) => dataOf<boolean>(http.get<boolean>(`/follow/or/not/${userId}`))
export const setFollow = (userId: number, toFollow: boolean) => dataOf<void>(http.put(`/follow/${userId}/${toFollow}`))
export const getCommonFollows = (userId: number) => dataOf<User[]>(http.get<User[]>(`/follow/common/${userId}`))
