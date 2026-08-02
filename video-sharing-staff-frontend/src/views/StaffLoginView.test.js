import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  push: vi.fn(),
  save: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../services/api', () => ({
  default: { post: mocks.post },
}))

vi.mock('../services/staffAuth', () => ({
  saveStaffSession: mocks.save,
  isStaffRole: (role) => ['REVIEWER', 'ADMIN'].includes(role),
}))

import StaffLoginView from './StaffLoginView.vue'

describe('staff login', () => {
  beforeEach(() => {
    mocks.post.mockReset()
    mocks.push.mockReset()
    mocks.save.mockReset()
  })

  it('logs a reviewer in once, saves staff state, and opens moderation', async () => {
    let resolveLogin
    mocks.post.mockReturnValue(new Promise((resolve) => { resolveLogin = resolve }))
    const wrapper = mount(StaffLoginView)

    await wrapper.get('#username').setValue('reviewer-one')
    await wrapper.get('#password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith('/api/staff/auth/login', {
      username: 'reviewer-one',
      password: 'password123',
    })
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    const payload = { token: 'token', username: 'reviewer-one', id: 3, role: 'REVIEWER' }
    resolveLogin({ data: payload })
    await flushPromises()

    expect(mocks.save).toHaveBeenCalledWith(payload)
    expect(mocks.push).toHaveBeenCalledWith({ name: 'moderation' })
  })

  it('opens reviewer management after an admin logs in', async () => {
    mocks.post.mockResolvedValue({
      data: { token: 'token', username: 'admin', id: 1, role: 'ADMIN' },
    })
    const wrapper = mount(StaffLoginView)

    await wrapper.get('#username').setValue('admin')
    await wrapper.get('#password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.push).toHaveBeenCalledWith({ name: 'reviewers' })
  })

  it('distinguishes invalid credentials from an insufficient role without raw payloads', async () => {
    mocks.post
      .mockRejectedValueOnce({ response: { status: 401, data: 'raw backend credentials error' } })
      .mockResolvedValueOnce({ data: { token: 'token', username: 'viewer', role: 'USER' } })
    const wrapper = mount(StaffLoginView)

    await wrapper.get('#username').setValue('viewer')
    await wrapper.get('#password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('用户名或密码不正确。')
    expect(wrapper.text()).not.toContain('raw backend credentials error')

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('此账号没有员工工作台权限。')
    expect(mocks.save).not.toHaveBeenCalled()
  })
})
