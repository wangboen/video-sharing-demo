import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('../services/api', () => ({ default: { get: mocks.get } }))

import HomeView from './HomeView.vue'

const global = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  },
}

describe('HomeView', () => {
  beforeEach(() => mocks.get.mockReset())

  it('distinguishes an empty library and guides creators to upload', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    const wrapper = mount(HomeView, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('还没有视频')
    expect(wrapper.text()).toContain('发布第一段作品')
    expect(wrapper.get('a[href="/upload"]').text()).toContain('发布视频')
  })

  it('keeps category-no-match copy distinct from an empty library', async () => {
    mocks.get.mockResolvedValue({ data: [{ id: 1, title: '科技前端分享', description: '' }] })
    const wrapper = mount(HomeView, { global })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '游戏').trigger('click')

    expect(wrapper.text()).toContain('还没有匹配的视频')
    expect(wrapper.text()).not.toContain('发布第一段作品')
  })
})
