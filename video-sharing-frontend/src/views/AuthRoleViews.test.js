import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  push: vi.fn(),
  saveAuth: vi.fn(),
  clearAuth: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ currentRoute: { value: { query: {} } }, push: mocks.push }),
}))

vi.mock('../services/api', () => ({ default: { post: mocks.post } }))
vi.mock('../services/auth', () => ({
  saveAuth: mocks.saveAuth,
  clearAuth: mocks.clearAuth,
  USER_ROLE: 'USER',
  PUBLIC_ACCESS_ERROR: '仅支持普通用户登录，请使用普通用户账号。',
}))

import LoginView from './LoginView.vue'
import RegisterView from './RegisterView.vue'

const global = { stubs: { RouterLink: { template: '<a><slot /></a>' } } }

describe('ordinary-user auth views', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset())
  })

  it.each([
    [LoginView, '/api/auth/login'],
    [RegisterView, '/api/auth/register'],
  ])('rejects a non-USER response from %s without persisting or navigating', async (View, endpoint) => {
    mocks.post.mockResolvedValue({ data: { token: 'staff-token', id: 9, username: 'staff-user', role: 'ADMIN' } })
    const wrapper = mount(View, { global })

    await wrapper.get('#username').setValue('staff-user')
    await wrapper.get('#password').setValue('password123')
    if (wrapper.find('#email').exists()) await wrapper.get('#email').setValue('staff@example.com')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith(endpoint, expect.any(Object))
    expect(mocks.saveAuth).not.toHaveBeenCalled()
    expect(mocks.clearAuth).toHaveBeenCalledOnce()
    expect(mocks.push).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('仅支持普通用户登录，请使用普通用户账号。')
  })
})
