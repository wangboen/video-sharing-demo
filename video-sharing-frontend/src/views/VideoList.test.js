import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
}))

vi.mock('../services/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

import VideoList from './VideoList.vue'
import api from '../services/api'

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: '<a :href="to"><slot /></a>',
    },
  },
}

describe('VideoList', () => {
  it('shows category-specific empty copy when loaded videos do not match the selected category', async () => {
    api.get.mockResolvedValue({
      data: [{ id: 1, title: '科技前端分享', description: '', user: { username: '小林' } }],
    })

    const wrapper = mount(VideoList, { global })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '游戏').trigger('click')

    expect(wrapper.text()).toContain('该分类暂时没有视频')
    expect(wrapper.text()).not.toContain('视频发布后会显示在这里。')
  })
})
