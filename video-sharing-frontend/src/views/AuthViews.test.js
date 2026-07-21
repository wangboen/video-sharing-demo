import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  push: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ currentRoute: { value: { query: {} } }, push: mocks.push }),
}))

vi.mock('../services/api', () => ({
  default: { post: mocks.post },
}))

vi.mock('../services/auth', () => ({
  saveAuth: vi.fn(),
}))

import LoginView from './LoginView.vue'
import RegisterView from './RegisterView.vue'

const global = {
  stubs: {
    RouterLink: { template: '<a><slot /></a>' },
  },
}

describe('认证页面', () => {
  beforeEach(() => {
    mocks.post.mockReset()
    mocks.push.mockReset()
  })

  it('以中文显示登录表单', () => {
    const wrapper = mount(LoginView, { global })

    expect(wrapper.classes()).toContain('auth-page')
    expect(wrapper.text()).toContain('用户名')
    expect(wrapper.text()).toContain('密码')
    expect(wrapper.text()).toContain('登录')
  })

  it('以中文显示注册表单', () => {
    const wrapper = mount(RegisterView, { global })

    expect(wrapper.classes()).toContain('auth-page')
    expect(wrapper.text()).toContain('用户名')
    expect(wrapper.text()).toContain('邮箱')
    expect(wrapper.text()).toContain('密码')
    expect(wrapper.text()).toContain('创建账号')
    expect(wrapper.text()).not.toContain('收藏喜欢的内容')
  })

  it.each([
    [LoginView, '/api/auth/login', '正在登录…'],
    [RegisterView, '/api/auth/register', '正在创建…'],
  ])('请求进行中禁用提交并阻止重复请求，完成后恢复', async (View, endpoint, loadingCopy) => {
    let resolveRequest
    const pending = new Promise((resolve) => { resolveRequest = resolve })
    mocks.post.mockReturnValue(pending)
    const wrapper = mount(View, { global })

    await wrapper.get('#username').setValue('vision-user')
    await wrapper.get('#password').setValue('password123')
    if (wrapper.find('#email').exists()) await wrapper.get('#email').setValue('vision@example.com')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith(endpoint, expect.any(Object))
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[type="submit"]').text()).toBe(loadingCopy)

    resolveRequest({ data: { token: 'token', id: 1, username: 'vision-user' } })
    await flushPromises()

    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('登录失败后显示中文友好错误且允许再次提交', async () => {
    mocks.post
      .mockRejectedValueOnce({ response: { data: { message: 'Invalid username or password' } } })
      .mockResolvedValueOnce({ data: { token: 'token', id: 1, username: 'vision-user' } })
    const wrapper = mount(LoginView, { global })

    await wrapper.get('#username').setValue('vision-user')
    await wrapper.get('#password').setValue('bad-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('用户名或密码不正确。')
    expect(wrapper.text()).not.toContain('Invalid username or password')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledTimes(2)
  })
})
