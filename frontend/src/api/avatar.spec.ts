import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDefaultAvatars, saveAvatar } from './user'
import { uploadAvatar } from './upload'

const { get, post, put } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('./http', () => ({
  http: { get, post, put },
  dataOf: <T>(request: Promise<T>) => request,
}))

describe('avatar API', () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset() })

  it('loads the fixed default avatar catalog', async () => {
    get.mockResolvedValue([{ id: 'default-01', icon: '/imgs/icons/default-01.png' }])

    await getDefaultAvatars()

    expect(get).toHaveBeenCalledWith('/user/avatar/defaults')
  })

  it('uploads an avatar as multipart image data', async () => {
    const file = new File(['png'], 'avatar.png', { type: 'image/png' })
    post.mockResolvedValue('/imgs/avatars/tmp/7/avatar.png')

    await uploadAvatar(file)

    const [, form] = post.mock.calls[0]
    expect(post.mock.calls[0][0]).toBe('/upload/avatar')
    expect(form).toBeInstanceOf(FormData)
    expect(form.get('image')).toBe(file)
  })

  it('saves a selected avatar source', async () => {
    put.mockResolvedValue({ icon: '/imgs/icons/default-01.png', sourceType: 'DEFAULT' })

    await saveAvatar({ sourceType: 'DEFAULT', defaultId: 'default-01' })

    expect(put).toHaveBeenCalledWith('/user/avatar', { sourceType: 'DEFAULT', defaultId: 'default-01' })
  })
})
