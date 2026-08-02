import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import VideoListItem from './VideoListItem.vue'

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: '<a :href="to"><slot /></a>',
    },
  },
}

describe('VideoListItem', () => {
  it('renders a recommendation thumbnail, title, author, and video route', () => {
    const wrapper = mount(VideoListItem, {
      global,
      props: {
        video: {
          id: 9,
          title: '夜晚城市',
          thumbnailUrl: '/media/city-night.jpg',
          uploader: 'Safe list author',
        },
      },
    })

    expect(wrapper.get('img').attributes('src')).toBe('http://localhost:8080/media/city-night.jpg')
    expect(wrapper.text()).toContain('夜晚城市')
    expect(wrapper.text()).toContain('Safe list author')
    expect(wrapper.get('a').attributes('href')).toBe('/video/9')
  })

  it('shows the Vision brand placeholder when a recommendation has no thumbnail', () => {
    const wrapper = mount(VideoListItem, {
      global,
      props: {
        video: { id: 9, title: '夜晚城市', user: { username: '小林' } },
      },
    })

    expect(wrapper.get('[data-testid="thumbnail-placeholder"]').text()).toContain('视界')
  })

  it('switches to the branded fallback when a recommendation thumbnail fails', async () => {
    const wrapper = mount(VideoListItem, {
      global,
      props: { video: { id: 9, title: '损坏封面', thumbnailUrl: '/media/broken.jpg' } },
    })

    await wrapper.get('img').trigger('error')

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[data-testid="thumbnail-placeholder"]').text()).toContain('视界')
  })
})
