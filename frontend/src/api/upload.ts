import { dataOf, http } from './http'
export const normalizeBlogImageUrl = (path: string) => {
  if (!path || /^https?:\/\//i.test(path) || path.startsWith('/imgs/')) return path
  return `/imgs/${path.replace(/^\/+/, '')}`
}
export const uploadBlogImage = (file: File) => {
  const form = new FormData()
  form.append('image', file)
  return dataOf<string>(http.post<string>('/upload/blog/uploadImage', form))
}
export const deleteBlogImage = (path: string) => dataOf<string>(http.delete<string>('/upload/blog/deleteImage', {
  params: { name: path.replace(/^\/imgs(?=\/)/, '') },
}))
