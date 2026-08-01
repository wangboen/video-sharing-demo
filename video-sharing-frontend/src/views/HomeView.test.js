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

function page(content, overrides = {}) {
  return {
    data: {
      content,
      number: overrides.number ?? 0,
      totalPages: overrides.totalPages ?? (content.length ? 1 : 0),
      totalElements: overrides.totalElements ?? content.length,
    },
  }
}

describe('HomeView', () => {
  beforeEach(() => mocks.get.mockReset())

  it('loads the recommended video feed from the paged recommendations API', async () => {
    mocks.get.mockResolvedValue(page([]))

    const wrapper = mount(HomeView, { global })
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/videos/recommended', {
      params: { page: 0, size: 12 },
    })
    expect(wrapper.find('.video-search').exists()).toBe(false)
  })

  it('keeps category-no-match copy distinct from an empty library', async () => {
    mocks.get.mockResolvedValue(page([{ id: 1, title: 'Technology talk', description: '' }]))
    const wrapper = mount(HomeView, { global })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '游戏').trigger('click')

    expect(wrapper.text()).toContain('还没有匹配的视频')
    expect(wrapper.text()).not.toContain('发布第一段作品')
  })

  it('pages through recommended videos without rendering a local search form', async () => {
    mocks.get
      .mockResolvedValueOnce(page([{ id: 2, title: 'recommended item', description: '' }], {
        totalPages: 2,
        totalElements: 14,
      }))
      .mockResolvedValueOnce(page([{ id: 4, title: 'second page match', description: '' }], {
        number: 1,
        totalPages: 2,
        totalElements: 14,
      }))

    const wrapper = mount(HomeView, { global })
    await flushPromises()

    expect(wrapper.find('.video-search__input').exists()).toBe(false)
    await wrapper.get('.video-feed__page-next').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(2, '/api/videos/recommended', {
      params: { page: 1, size: 12 },
    })
    expect(wrapper.text()).toContain('second page match')
  })
})
