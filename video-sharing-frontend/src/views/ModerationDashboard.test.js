import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  push: vi.fn(),
  clearAuth: vi.fn(),
  getStoredUser: vi.fn(),
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('../services/api', () => ({ default: { get: mocks.get, post: mocks.post } }))
vi.mock('../services/auth', () => ({ clearAuth: mocks.clearAuth, getStoredUser: mocks.getStoredUser }))

import UserDashboard from './UserDashboard.vue'

const global = { stubs: { RouterLink: true } }

describe('moderation submissions dashboard', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset())
    mocks.getStoredUser.mockReturnValue({ username: 'vision-user', email: 'vision@example.com', role: 'USER' })
  })

  it('loads every submission with Chinese status labels, rejection reason, and timestamps', async () => {
    mocks.get.mockResolvedValue({
      data: [
        { id: 1, title: 'Pending video', status: 'PENDING', uploadDate: '2026-07-20T10:00:00' },
        { id: 2, title: 'Approved video', status: 'APPROVED', uploadDate: '2026-07-21T10:00:00', reviewedAt: '2026-07-24T11:00:00' },
        { id: 3, title: 'Rejected video', status: 'REJECTED', uploadDate: '2026-07-22T10:00:00', reviewedAt: '2026-07-24T12:00:00', rejectionReason: '  Needs a clearer title  ' },
      ],
    })

    const wrapper = mount(UserDashboard, { global })
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/users/me/videos')
    expect(wrapper.text()).toContain('待审核')
    expect(wrapper.text()).toContain('已通过')
    expect(wrapper.text()).toContain('已驳回')
    expect(wrapper.text()).toContain('Needs a clearer title')
    expect(wrapper.text()).toContain('2026-07-20')
    expect(wrapper.text()).toContain('2026-07-24')
    expect(wrapper.text()).toContain('上传时间')
    expect(wrapper.text()).toContain('审核时间')
  })

  it('shows loading before an empty response using existing state components', async () => {
    let resolveRequest
    mocks.get.mockReturnValue(new Promise((resolve) => { resolveRequest = resolve }))
    const wrapper = mount(UserDashboard, { global })
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent({ name: 'LoadingState' }).exists()).toBe(true)

    resolveRequest({ data: [] })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(true)
  })

  it('shows the existing error state when the submissions API fails', async () => {
    mocks.get.mockRejectedValue(new Error('network failure'))
    const wrapper = mount(UserDashboard, { global })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ErrorState' }).exists()).toBe(true)
  })
})
