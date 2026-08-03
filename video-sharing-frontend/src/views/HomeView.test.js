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

  it('shows a friendly service error when recommendations fail', async () => {
    const error = new Error('service unavailable')
    error.response = { status: 503, data: { message: 'Service is temporarily unavailable' } }
    mocks.get.mockResolvedValue({
      get data() {
        throw error
      },
    })

    const wrapper = mount(HomeView, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('服务暂时不可用，请稍后重试。')
  })

  it('keeps category-no-match copy distinct from an empty library', async () => {
    mocks.get.mockImplementation((url, options) => {
      if (url === '/api/categories') return Promise.resolve({ data: [{ id: 1, name: '电影' }] })
      if (url === '/api/videos/recommended' && options?.params?.categoryId === 1) {
        return Promise.resolve(page([]))
      }
      return Promise.resolve(page([{ id: 1, title: 'Technology talk', description: '' }]))
    })
    const wrapper = mount(HomeView, { global })
    await flushPromises()
    await wrapper.get('[data-testid="recommendation-category-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('还没有匹配的视频')
    expect(wrapper.text()).not.toContain('发布第一段作品')
  })

  it('pages through recommended videos without rendering a local search form', async () => {
    mocks.get.mockImplementation((url, options) => {
      if (url === '/api/categories') return Promise.resolve({ data: [] })
      if (url === '/api/videos/recommended' && options?.params?.page === 1) {
        return Promise.resolve(page([{ id: 4, title: 'second page match', description: '' }], {
          number: 1,
          totalPages: 2,
          totalElements: 14,
        }))
      }
      return Promise.resolve(page([{ id: 2, title: 'recommended item', description: '' }], {
        totalPages: 2,
        totalElements: 14,
      }))
    })

    const wrapper = mount(HomeView, { global })
    await flushPromises()

    expect(wrapper.find('.video-search__input').exists()).toBe(false)
    await wrapper.get('.video-feed__page-next').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/videos/recommended', {
      params: { page: 1, size: 12 },
    })
    expect(wrapper.text()).toContain('second page match')
  })

  it('loads current categories and filters recommendations by selected category', async () => {
    mocks.get.mockImplementation((url, options) => {
      if (url === '/api/categories') {
        return Promise.resolve({ data: [{ id: 1, name: '电影' }] })
      }
      if (url === '/api/videos/recommended' && options?.params?.categoryId === 1) {
        return Promise.resolve(page([{ id: 9, title: 'category recommendation', description: '' }]))
      }
      return Promise.resolve(page([{ id: 2, title: 'default recommendation', description: '' }]))
    })

    const wrapper = mount(HomeView, { global })
    await flushPromises()
    await wrapper.get('[data-testid="recommendation-category-1"]').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/categories')
    expect(mocks.get).toHaveBeenLastCalledWith('/api/videos/recommended', {
      params: { page: 0, size: 12, categoryId: 1 },
    })
    expect(wrapper.text()).toContain('category recommendation')
  })
})
