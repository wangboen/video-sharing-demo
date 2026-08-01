import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
  push: vi.fn(),
  clearAuth: vi.fn(),
  getStoredUser: vi.fn(),
  locationAssign: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get, post: mocks.post, delete: mocks.delete },
}))

vi.mock('../services/auth', () => ({
  clearAuth: mocks.clearAuth,
  getStoredUser: mocks.getStoredUser,
}))

import UserDashboard from './UserDashboard.vue'

describe('个人中心退出登录', () => {
  beforeEach(() => {
    mocks.post.mockReset()
    mocks.delete.mockReset()
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
      data: {
        content: [{
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
        number: 0,
        totalPages: 1,
        totalElements: 1,
      },
    })
    mocks.get.mockResolvedValueOnce({ data: [] })
    mocks.get.mockResolvedValueOnce({ data: { url: 'http://minio.local/download/video.mp4' } })

    const wrapper = mount(UserDashboard, { global: { stubs: { RouterLink: true } } })
    await flushPromises()

    const buttons = wrapper.findAll('.submission-download')
    expect(buttons).toHaveLength(2)
    await buttons[0].trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(3, '/api/users/me/videos/42/download/video', {
      params: { redirect: false },
    })
    expect(mocks.locationAssign).toHaveBeenCalledWith('http://minio.local/download/video.mp4')
    expect(wrapper.text()).toContain('可下载至')
  })

  it('loads the next page of submissions from the paged API response', async () => {
    mocks.get.mockResolvedValueOnce({
      data: {
        content: [{
          id: 51,
          title: 'page one',
          status: 'PENDING',
          uploadDate: '2026-07-31T08:00:00',
          canDownloadVideo: true,
          canDownloadThumbnail: false,
        }],
        number: 0,
        totalPages: 2,
        totalElements: 7,
      },
    })
    mocks.get.mockResolvedValueOnce({ data: [] })
    mocks.get.mockResolvedValueOnce({
      data: {
        content: [{
          id: 52,
          title: 'page two',
          status: 'APPROVED',
          uploadDate: '2026-07-30T08:00:00',
          canDownloadVideo: true,
          canDownloadThumbnail: true,
        }],
        number: 1,
        totalPages: 2,
        totalElements: 7,
      },
    })

    const wrapper = mount(UserDashboard, { global: { stubs: { RouterLink: true } } })
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(1, '/api/users/me/videos', {
      params: { page: 0, size: 6 },
    })
    expect(wrapper.text()).toContain('page one')

    await wrapper.get('.submission-page-next').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(3, '/api/users/me/videos', {
      params: { page: 1, size: 6 },
    })
    expect(wrapper.text()).toContain('page two')
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

  it('removes a personal comment after anonymizing it', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [{ commentId: 7, content: 'my comment', createdAt: '2026-08-01T08:00:00Z' }] })
    mocks.delete.mockResolvedValue({})

    const wrapper = mount(UserDashboard, { global: { stubs: { RouterLink: true } } })
    await flushPromises()

    expect(wrapper.text()).toContain('my comment')
    await wrapper.get('.personal-comment-delete').trigger('click')
    await flushPromises()

    expect(mocks.delete).toHaveBeenCalledWith('/api/users/me/comments/7')
    expect(wrapper.text()).not.toContain('my comment')
  })
})
