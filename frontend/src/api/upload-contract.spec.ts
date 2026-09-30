import { beforeEach, describe, expect, it, vi } from 'vitest'

const { post, del } = vi.hoisted(() => ({ post: vi.fn(), del: vi.fn() }))

vi.mock('./http', () => ({
  http: { post, delete: del },
  dataOf: (request: unknown) => request,
}))

import { deleteBlogImage, uploadBlogImage } from './upload'

describe('upload API contract', () => {
  beforeEach(() => {
    post.mockReset()
    del.mockReset()
    post.mockResolvedValue({})
    del.mockResolvedValue({})
  })

  it('uploads a blog image using the backend field and endpoint', async () => {
    const file = new File(['image'], 'coffee.jpg', { type: 'image/jpeg' })

    await uploadBlogImage(file)

    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][0]).toBe('/upload/blog/uploadImage')
    expect(post.mock.calls[0][1]).toBeInstanceOf(FormData)
    expect(post.mock.calls[0][1].get('image')).toBe(file)
  })

  it('deletes a blog image with the backend delete endpoint', async () => {
    await deleteBlogImage('/imgs/blogs/a/3/coffee.jpg')

    expect(del).toHaveBeenCalledWith('/upload/blog/deleteImage', {
      params: { name: '/blogs/a/3/coffee.jpg' },
    })
  })
})
