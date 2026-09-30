import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('./http', () => ({
  http: { get, post },
  dataOf: (request: unknown) => request,
}))

import { createBlogComment, getBlog, getBlogComments, publishBlog } from './blog'

describe('blog detail API', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    get.mockResolvedValue({})
    post.mockResolvedValue({})
  })

  it('requests a blog detail through the view endpoint', async () => {
    await getBlog(123)

    expect(get).toHaveBeenCalledWith('/blog/view/123')
  })

  it('publishes a blog through the post endpoint', async () => {
    const payload = { shopId: 7, title: '午后咖啡', content: '值得再来', images: '/imgs/blogs/a/3/coffee.jpg' }

    await publishBlog(payload)

    expect(post).toHaveBeenCalledWith('/blog/post', payload)
  })

  it('loads paged blog comments', async () => {
    await getBlogComments(123, 2)

    expect(get).toHaveBeenCalledWith('/blog/123/comments', { params: { current: 2 } })
  })

  it('creates a blog comment through the nested comments endpoint', async () => {
    await createBlogComment(123, '很棒')

    expect(post).toHaveBeenCalledWith('/blog/123/comments', { content: '很棒' })
  })
})
