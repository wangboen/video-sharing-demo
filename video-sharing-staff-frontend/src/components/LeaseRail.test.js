import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import LeaseRail from './LeaseRail.vue'

describe('lease countdown timezone handling', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows a fifteen-minute lease in an Asia/Shanghai browser for a UTC API timestamp', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-24T08:00:00+08:00'))

    const wrapper = mount(LeaseRail, {
      props: {
        expiresAt: '2026-07-24T00:15:00Z',
      },
    })

    expect(wrapper.text()).toContain('00:15:00')
    wrapper.unmount()
  })
})
