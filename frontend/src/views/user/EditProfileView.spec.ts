import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EditProfileView from './EditProfileView.vue'

const api = vi.hoisted(() => ({
  getUserInfo: vi.fn(),
  getDefaultAvatars: vi.fn(),
  saveAvatar: vi.fn(),
  uploadAvatar: vi.fn(),
}))
const auth = vi.hoisted(() => ({
  user: { id: 7, nickName: '星', icon: '/imgs/old.png' },
  fetchCurrentUser: vi.fn(),
  updateAvatar: vi.fn(),
}))
vi.mock('@/api/user', () => api)
vi.mock('@/api/upload', () => ({ uploadAvatar: api.uploadAvatar }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

describe('EditProfileView avatar editor', () => {
  beforeEach(() => {
    api.getUserInfo.mockResolvedValue({ city: '杭州' })
    api.getDefaultAvatars.mockResolvedValue([
      { id: 'default-01', icon: '/imgs/icons/default-01.png' },
      { id: 'default-02', icon: '/imgs/icons/default-02.png' },
    ])
    api.saveAvatar.mockResolvedValue({ icon: '/imgs/icons/default-01.png', sourceType: 'DEFAULT' })
    api.uploadAvatar.mockResolvedValue('/imgs/avatars/tmp/7/upload.png')
    auth.fetchCurrentUser.mockResolvedValue(auth.user)
    auth.updateAvatar.mockReset()
  })

  it('选择默认头像后只有点击保存才提交', async () => {
    const wrapper = mount(EditProfileView)
    await flushPromises()

    await wrapper.get('[data-test="default-avatar-default-01"]').trigger('click')
    expect(api.saveAvatar).not.toHaveBeenCalled()

    await wrapper.get('[data-test="save-avatar"]').trigger('click')
    await flushPromises()

    expect(api.saveAvatar).toHaveBeenCalledWith({ sourceType: 'DEFAULT', defaultId: 'default-01' })
    expect(auth.updateAvatar).toHaveBeenCalledWith('/imgs/icons/default-01.png')
  })

  it('上传图片后预览临时路径并在保存时绑定', async () => {
    const wrapper = mount(EditProfileView)
    await flushPromises()
    const input = wrapper.get('input[type="file"]')
    const file = new File(['png'], 'avatar.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })

    await input.trigger('change')
    await flushPromises()
    await wrapper.get('[data-test="save-avatar"]').trigger('click')
    await flushPromises()

    expect(api.uploadAvatar).toHaveBeenCalledWith(file)
    expect(api.saveAvatar).toHaveBeenCalledWith({ sourceType: 'UPLOAD', uploadPath: '/imgs/avatars/tmp/7/upload.png' })
  })

  it('保存失败时保留原头像并显示错误', async () => {
    api.saveAvatar.mockRejectedValue(new Error('保存失败'))
    const wrapper = mount(EditProfileView)
    await flushPromises()

    await wrapper.get('[data-test="default-avatar-default-01"]').trigger('click')
    await wrapper.get('[data-test="save-avatar"]').trigger('click')
    await flushPromises()

    expect(auth.updateAvatar).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="avatar-error"]').text()).toContain('保存失败')
  })
})
