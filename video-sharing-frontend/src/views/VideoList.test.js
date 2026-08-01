import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const route = vi.hoisted(() => ({ query: {} }))
const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('vue-router', () => ({ useRoute: () => route }))

vi.mock('../services/api', () => ({
  default: {
    get: mocks.get,
  },
}))

import VideoList from './VideoList.vue'

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: '<a :href="to"><slot /></a>',
    },
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

describe('VideoList', () => {
  beforeEach(() => {
    route.query = {}
    mocks.get.mockReset()
  })

  it('shows category-specific empty copy when loaded videos do not match the selected category', async () => {
    mocks.get.mockResolvedValue(page([{ id: 1, title: 'Technology talk', description: '', user: { username: '小林' } }]))

    const wrapper = mount(VideoList, { global })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '游戏').trigger('click')

    expect(wrapper.text()).toContain('该分类暂时没有视频')
    expect(wrapper.text()).not.toContain('视频发布后会显示在这里。')
  })

  it('loads paged results from the search API for the global header query', async () => {
    route.query = { q: 'spring' }
    mocks.get
      .mockResolvedValueOnce(page([{ id: 2, title: 'first match', description: '' }], {
        totalPages: 2,
        totalElements: 14,
      }))
      .mockResolvedValueOnce(page([{ id: 3, title: 'second page match', description: '' }], {
        number: 1,
        totalPages: 2,
        totalElements: 14,
      }))

    const wrapper = mount(VideoList, { global })
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(1, '/api/videos/search', {
      params: { q: 'spring', page: 0, size: 12 },
    })
    expect(wrapper.text()).toContain('first match')

    await wrapper.get('.video-list__page-next').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(2, '/api/videos/search', {
      params: { q: 'spring', page: 1, size: 12 },
    })
    expect(wrapper.text()).toContain('second page match')
  })
})
