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
  reviewedTotal: 12,
  approvedTotal: 8,
  rejectedTotal: 4,
  lastReviewedAt: '2026-07-26T02:30:00Z',
}

const reviewerPage = {
  content: [reviewer],
  number: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

const reviewRecord = {
  videoId: 42,
  title: 'Kitchen clip',
  uploader: 'creator-one',
  status: 'REJECTED',
  reviewedAt: '2026-07-26T02:30:00Z',
  rejectionReason: 'unsafe food',
}

const reviewPage = {
  content: [reviewRecord],
  number: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

describe('admin reviewer supervision', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
  })

  it('loads a paged searchable reviewer list with supervision totals', async () => {
    mocks.get.mockResolvedValue({ data: reviewerPage })
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/admin/reviewers', {
      params: { page: 0, size: 20, q: '' },
    })
    expect(wrapper.text()).toContain('reviewer-nine')
    expect(wrapper.text()).toContain('未填写邮箱')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('2026/07/26 10:30')
  })

  it('searches reviewers from the first page and keeps the term for pagination', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: { ...reviewerPage, totalPages: 2 } })
      .mockResolvedValueOnce({ data: { ...reviewerPage, number: 0, totalPages: 2 } })
      .mockResolvedValueOnce({ data: { ...reviewerPage, number: 1, totalPages: 2 } })
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    await wrapper.get('[data-test="reviewer-search"]').setValue('nine')
    await wrapper.get('[data-test="reviewer-search-submit"]').trigger('submit')
    await flushPromises()

    expect(mocks.get).toHaveBeenLastCalledWith('/api/admin/reviewers', {
      params: { page: 0, size: 20, q: 'nine' },
    })

    await wrapper.get('[data-test="reviewers-next"]').trigger('click')
    await flushPromises()
    expect(mocks.get).toHaveBeenLastCalledWith('/api/admin/reviewers', {
      params: { page: 1, size: 20, q: 'nine' },
    })
  })

  it('loads searchable paged review records for the selected reviewer', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: reviewerPage })
      .mockResolvedValueOnce({ data: { ...reviewPage, totalPages: 2 } })
      .mockResolvedValueOnce({ data: { ...reviewPage, content: [], totalElements: 0, totalPages: 0 } })
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    await wrapper.get('[data-test="reviews-9"]').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenLastCalledWith('/api/admin/reviewers/9/reviews', {
      params: { page: 0, size: 20, q: '' },
    })
    expect(wrapper.text()).toContain('Kitchen clip')
    expect(wrapper.text()).toContain('creator-one')
    expect(wrapper.text()).toContain('REJECTED')
    expect(wrapper.text()).toContain('unsafe food')

    await wrapper.get('[data-test="review-search"]').setValue('policy')
    await wrapper.get('[data-test="review-search-submit"]').trigger('submit')
    await flushPromises()

    expect(mocks.get).toHaveBeenLastCalledWith('/api/admin/reviewers/9/reviews', {
      params: { page: 0, size: 20, q: 'policy' },
    })
    expect(wrapper.get('[data-test="empty-reviews"]').text()).toContain('没有匹配的审核记录')
  })

  it('creates once, clears the form, and adds the returned reviewer summary', async () => {
    mocks.get.mockResolvedValue({ data: { ...reviewerPage, content: [] } })
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

    resolveCreate({ status: 201, data: reviewer })
    await flushPromises()
    expect(wrapper.text()).toContain('审核员已创建')
    expect(wrapper.text()).toContain('reviewer-nine')
    expect(wrapper.get('#reviewer-username').element.value).toBe('')
    expect(wrapper.get('#reviewer-password').element.value).toBe('')
  })

  it('shows stable validation and service errors without leaking raw backend details', async () => {
    mocks.get.mockResolvedValue({ data: { ...reviewerPage, content: [] } })
    mocks.post.mockRejectedValueOnce({ response: { status: 400, data: 'raw duplicate details' } })
    const wrapper = mount(ReviewerManagementView)
    await flushPromises()

    await wrapper.get('#reviewer-username').setValue('ab')
    await wrapper.get('#reviewer-password').setValue('short')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('用户名需为 3-50 个字符')
    expect(mocks.post).not.toHaveBeenCalled()

    await wrapper.get('#reviewer-username').setValue('reviewer-nine')
    await wrapper.get('#reviewer-password').setValue('password123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('用户名或邮箱已被使用，请更换后重试。')
    expect(wrapper.text()).not.toContain('raw duplicate details')
  })
})
