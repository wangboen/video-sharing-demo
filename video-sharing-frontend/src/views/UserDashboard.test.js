import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  push: vi.fn(),
  clearAuth: vi.fn(),
  getStoredUser: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../services/api', () => ({
  default: { post: mocks.post },
}))

vi.mock('../services/auth', () => ({
  clearAuth: mocks.clearAuth,
  getStoredUser: mocks.getStoredUser,
}))

import UserDashboard from './UserDashboard.vue'

describe('个人中心退出登录', () => {
  beforeEach(() => {
    mocks.post.mockReset()
    mocks.push.mockReset()
    mocks.clearAuth.mockReset()
    mocks.getStoredUser.mockReturnValue({ username: 'vision-user', email: 'vision@example.com' })
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
})
