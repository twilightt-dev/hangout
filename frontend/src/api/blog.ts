import { dataOf, http } from './http'
import type { Blog, Comment, PageResult, ScrollResult, User } from '@/types/api'
export const getHotBlogs = (current = 1) => dataOf<Blog[]>(http.get<Blog[]>('/blog/hot', { params: { current } }))
export const getBlog = (id: number) => dataOf<Blog>(http.get<Blog>(`/blog/view/${id}`))
export const getBlogLikes = (id: number) => dataOf<User[]>(http.get<User[]>(`/blog/likes/${id}`))
export const likeBlog = (id: number) => dataOf<void>(http.put(`/blog/like/${id}`))
export const getMyBlogs = (current = 1) => dataOf<Blog[]>(http.get<Blog[]>('/blog/of/me', { params: { current } }))
export const getBlogsByUser = (id: number, current = 1) => dataOf<Blog[]>(http.get<Blog[]>('/blog/of/user', { params: { id, current } }))
export const getFollowFeed = (lastId: number, offset: number) => dataOf<ScrollResult<Blog>>(http.get<ScrollResult<Blog>>('/blog/of/follow', { params: { lastId, offset } }))
export const publishBlog = (payload: Partial<Blog>) => dataOf<number>(http.post<number>('/blog/post', payload))
export const getBlogComments = (blogId: number, current = 1) =>
  dataOf<PageResult<Comment>>(http.get<PageResult<Comment>>(`/blog/${blogId}/comments`, { params: { current } }))
export const createBlogComment = (blogId: number, content: string) =>
  dataOf<number>(http.post<number>(`/blog/${blogId}/comments`, { content }))
