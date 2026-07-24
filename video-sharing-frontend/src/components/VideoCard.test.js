import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import VideoCard from './VideoCard.vue'

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: '<a :href="typeof to === \'string\' ? to : to.path"><slot /></a>',
    },
  },
}

describe('VideoCard', () => {
  it('shows a branded fallback and missing view copy for incomplete video data', () => {
    const wrapper = mount(VideoCard, {
      props: {
        video: {
          id: 7,
          title: '没有缩略图的视频',
          uploader: 'Safe card author',
          user: { username: '小林' },
        },
      },
      global,
    })

    expect(wrapper.get('[data-testid="thumbnail-placeholder"]').text()).toContain('视界')
    expect(wrapper.text()).toContain('Safe card author')
    expect(wrapper.text()).not.toContain('小林')
    expect(wrapper.text()).toContain('暂无播放数据')
    expect(wrapper.get('.video-card__title').attributes('href')).toBe('/video/7')
  })

  it('switches to the branded fallback when the thumbnail fails to load', async () => {
    const wrapper = mount(VideoCard, {
      props: { video: { id: 7, title: '损坏封面', thumbnailUrl: '/media/broken.jpg' } },
      global,
    })

    await wrapper.get('img').trigger('error')

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[data-testid="thumbnail-placeholder"]').text()).toContain('视界')
  })
})
