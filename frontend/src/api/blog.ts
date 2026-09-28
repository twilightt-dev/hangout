import { dataOf, http } from './http'
import type { Blog, ScrollResult, User } from '@/types/api'
export const getHotBlogs = (current = 1) => dataOf<Blog[]>(http.get<Blog[]>('/blog/hot', { params: { current } }))
export const getBlog = (id: number) => dataOf<Blog>(http.get<Blog>(`/blog/view/${id}`))
export const getBlogLikes = (id: number) => dataOf<User[]>(http.get<User[]>(`/blog/likes/${id}`))
export const likeBlog = (id: number) => dataOf<void>(http.put(`/blog/like/${id}`))
export const getMyBlogs = (current = 1) => dataOf<Blog[]>(http.get<Blog[]>('/blog/of/me', { params: { current } }))
export const getBlogsByUser = (id: number, current = 1) => dataOf<Blog[]>(http.get<Blog[]>('/blog/of/user', { params: { id, current } }))
export const getFollowFeed = (lastId: number, offset: number) => dataOf<ScrollResult<Blog>>(http.get<ScrollResult<Blog>>('/blog/of/follow', { params: { lastId, offset } }))
export const publishBlog = (payload: Partial<Blog>) => dataOf<number>(http.post<number>('/blog/post', payload))
