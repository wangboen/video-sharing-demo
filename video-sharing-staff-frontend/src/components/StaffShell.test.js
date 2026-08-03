import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  push: vi.fn(),
  clear: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))
vi.mock('../services/api', () => ({ default: { post: mocks.post } }))
vi.mock('../services/staffAuth', () => ({ clearStaffSession: mocks.clear }))

import StaffShell from './StaffShell.vue'

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: '<a :data-route="to.name"><slot /></a>',
    },
  },
}

describe('staff application shell', () => {
  beforeEach(() => {
    mocks.post.mockReset()
    mocks.push.mockReset()
    mocks.clear.mockReset()
  })

  it('shows role-appropriate navigation', () => {
    const reviewer = mount(StaffShell, {
      props: { user: { username: 'reviewer', role: 'REVIEWER' } },
      slots: { default: '<main>work</main>' },
      global,
    })
    const admin = mount(StaffShell, {
      props: { user: { username: 'admin', role: 'ADMIN' } },
      global,
    })

    expect(reviewer.findAll('[data-route="moderation"]')).toHaveLength(2)
    expect(reviewer.find('[data-route="reviewers"]').exists()).toBe(false)
    expect(reviewer.find('[data-route="taxonomy"]').exists()).toBe(false)
    expect(admin.findAll('[data-route="moderation"]')).toHaveLength(0)
    expect(admin.findAll('[data-route="reviewers"]')).toHaveLength(2)
    expect(admin.findAll('[data-route="taxonomy"]')).toHaveLength(1)
  })

  it('clears local staff state and returns to login even when logout fails', async () => {
    mocks.post.mockRejectedValue(new Error('offline'))
    const wrapper = mount(StaffShell, {
      props: { user: { username: 'reviewer', role: 'REVIEWER' } },
      global,
    })

    await wrapper.get('[data-test="logout"]').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/api/auth/logout')
    expect(mocks.clear).toHaveBeenCalledTimes(1)
    expect(mocks.push).toHaveBeenCalledWith({ name: 'login' })
  })
})
