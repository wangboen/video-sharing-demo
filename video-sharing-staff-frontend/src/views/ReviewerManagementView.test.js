import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get, post: mocks.post },
}))

import ReviewerManagementView from './ReviewerManagementView.vue'

const reviewer = {
  id: 9,
  username: 'reviewer-nine',
  email: null,
  role: 'REVIEWER',
  createdAt: '2026-07-24T09:30:00',
}

describe('admin reviewer management', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
  })

  it('loads reviewer details and renders the email fallback', async () => {
    mocks.get.mockResolvedValue({ data: [reviewer] })
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/admin/reviewers')
    expect(wrapper.text()).toContain('reviewer-nine')
    expect(wrapper.text()).toContain('未填写邮箱')
    expect(wrapper.text()).toContain('REVIEWER')
    expect(wrapper.text()).toContain('2026/07/24 09:30')
  })

  it('shows stable loading, empty, and service states', async () => {
    let resolveList
    mocks.get.mockReturnValue(new Promise((resolve) => { resolveList = resolve }))
    const wrapper = mount(ReviewerManagementView)
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载审核员')

    resolveList({ data: [] })
    await flushPromises()
    expect(wrapper.get('[data-test="empty-reviewers"]').text()).toContain('尚未创建审核员')

    mocks.get.mockRejectedValueOnce(new Error('service raw details'))
    await wrapper.get('[data-test="refresh"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('审核员列表暂时无法加载，请重试。')
    expect(wrapper.text()).not.toContain('service raw details')
  })

  it('validates username, optional email, and password before creation', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    expect(wrapper.get('form').attributes('novalidate')).toBeDefined()
    await wrapper.get('#reviewer-username').setValue('ab')
    await wrapper.get('#reviewer-email').setValue('not-an-email')
    await wrapper.get('#reviewer-password').setValue('short')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain('用户名需为 3–50 个字符')
    expect(mocks.post).not.toHaveBeenCalled()

    await wrapper.get('#reviewer-username').setValue('reviewer-ten')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('邮箱格式不正确')

    await wrapper.get('#reviewer-email').setValue('')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('初始密码需为 8–72 个字符')
  })

  it('creates once, clears the form, and adds the returned reviewer', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    let resolveCreate
    mocks.post.mockReturnValue(new Promise((resolve) => { resolveCreate = resolve }))
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    await wrapper.get('#reviewer-username').setValue('reviewer-nine')
    await wrapper.get('#reviewer-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith('/api/admin/reviewers', {
      username: 'reviewer-nine',
      email: '',
      password: 'password123',
    })
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    resolveCreate({ status: 201, data: reviewer })
    await flushPromises()
    expect(wrapper.text()).toContain('审核员已创建')
    expect(wrapper.text()).toContain('reviewer-nine')
    expect(wrapper.get('#reviewer-username').element.value).toBe('')
    expect(wrapper.get('#reviewer-password').element.value).toBe('')
  })

  it('shows a stable duplicate error and permits retry', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    mocks.post
      .mockRejectedValueOnce({ response: { status: 400, data: 'raw duplicate details' } })
      .mockResolvedValueOnce({ status: 201, data: reviewer })
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    await wrapper.get('#reviewer-username').setValue('reviewer-nine')
    await wrapper.get('#reviewer-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('用户名或邮箱已被使用，请更换后重试。')
    expect(wrapper.text()).not.toContain('raw duplicate details')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.post).toHaveBeenCalledTimes(2)
  })
})
