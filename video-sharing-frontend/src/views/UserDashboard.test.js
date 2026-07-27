import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  push: vi.fn(),
  clearAuth: vi.fn(),
  getStoredUser: vi.fn(),
  locationAssign: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get, post: mocks.post },
}))

vi.mock('../services/auth', () => ({
  clearAuth: mocks.clearAuth,
  getStoredUser: mocks.getStoredUser,
}))

import UserDashboard from './UserDashboard.vue'

describe('个人中心退出登录', () => {
  beforeEach(() => {
    mocks.post.mockReset()
    mocks.get.mockReset()
    mocks.get.mockResolvedValue({ data: [] })
    mocks.push.mockReset()
    mocks.clearAuth.mockReset()
    mocks.locationAssign.mockReset()
    mocks.getStoredUser.mockReturnValue({ username: 'vision-user', email: 'vision@example.com' })
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign: mocks.locationAssign, pathname: '/dashboard', search: '' },
    })
  })

  it('退出时通知服务端并清除当前浏览器会话', async () => {
    mocks.post.mockResolvedValue({})
    const wrapper = mount(UserDashboard, { global: { stubs: { RouterLink: true } } })

    await wrapper.find('.channel-actions button').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/api/auth/logout')
    expect(mocks.clearAuth).toHaveBeenCalledOnce()
    expect(mocks.push).toHaveBeenCalledWith('/login')
  })

  it('fetches an authenticated download target before opening MinIO', async () => {
    mocks.get.mockResolvedValueOnce({
      data: [{
        id: 42,
        title: '可下载投稿',
        status: 'REJECTED',
        rejectionReason: '画面重复',
        reviewedAt: '2026-07-26T08:00:00Z',
        uploadDate: '2026-07-25T08:00:00',
        canDownloadVideo: true,
        canDownloadThumbnail: true,
        downloadAvailableUntil: '2026-08-02T08:00:00Z',
      }],
    })
    mocks.get.mockResolvedValueOnce({ data: { url: 'http://minio.local/download/video.mp4' } })

    const wrapper = mount(UserDashboard, { global: { stubs: { RouterLink: true } } })
    await flushPromises()

    const buttons = wrapper.findAll('.submission-download')
    expect(buttons).toHaveLength(2)
    await buttons[0].trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(2, '/api/users/me/videos/42/download/video', {
      params: { redirect: false },
    })
    expect(mocks.locationAssign).toHaveBeenCalledWith('http://minio.local/download/video.mp4')
    expect(wrapper.text()).toContain('可下载至')
  })

  it('shows expired state when a rejected submission is no longer downloadable', async () => {
    mocks.get.mockResolvedValue({
      data: [{
        id: 43,
        title: '已过期投稿',
        status: 'REJECTED',
        rejectionReason: '不符合要求',
        reviewedAt: '2026-07-10T08:00:00Z',
        uploadDate: '2026-07-09T08:00:00',
        canDownloadVideo: false,
        canDownloadThumbnail: false,
        downloadAvailableUntil: '2026-07-17T08:00:00Z',
      }],
    })

    const wrapper = mount(UserDashboard, { global: { stubs: { RouterLink: true } } })
    await flushPromises()

    expect(wrapper.find('.submission-download').exists()).toBe(false)
    expect(wrapper.text()).toContain('下载已过期')
  })
})
