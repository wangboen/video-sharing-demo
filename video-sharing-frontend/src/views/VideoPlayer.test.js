import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  route: { params: { id: 'A' } },
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get },
}))

import VideoPlayer from './VideoPlayer.vue'

const global = {
  stubs: {
    VideoListItem: {
      props: ['video'],
      template: '<div class="video-list-item-stub">{{ video.title }}</div>',
    },
  },
}

function video(id, title = `video-${id}`) {
  return {
    id,
    title,
    videoUrl: `/uploads/${id}.mp4`,
    thumbnailUrl: `/thumbnails/${id}.jpg`,
    views: 12,
    uploadDate: '2026-07-20T00:00:00.000Z',
    description: `${title} description`,
    uploader: `${title} safe author`,
  }
}

describe('VideoPlayer', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.route.params = reactive({ id: 'A' })
  })

  it('loads the current video and recommendations from the list endpoint only', async () => {
    mocks.get.mockResolvedValue({ data: [video('A', 'current video'), video('B', 'recommended video')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(mocks.get.mock.calls.map(([url]) => url)).toEqual(['/api/videos'])
    expect(wrapper.text()).toContain('current video')
    expect(wrapper.text()).toContain('recommended video')
  })

  it('displays the safe DTO uploader as the current video author', async () => {
    mocks.get.mockResolvedValue({ data: [video('A', 'current video'), video('B')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.get('.author-panel__name').text()).toBe('current video safe author')
  })

  it('shows the page error state when the requested video is absent from the list', async () => {
    mocks.get.mockResolvedValue({ data: [video('B')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.find('.error-state').exists()).toBe(true)
  })

  it('uses the list endpoint again after route navigation and resets local actions', async () => {
    mocks.get.mockResolvedValue({ data: [video('A'), video('B')] })
    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    const actionButtons = wrapper.findAll('.video-actions button')
    await actionButtons[0].trigger('click')
    await actionButtons[1].trigger('click')

    mocks.route.params.id = 'B'
    await nextTick()
    await flushPromises()

    expect(mocks.get.mock.calls.map(([url]) => url)).toEqual(['/api/videos', '/api/videos'])
    expect(wrapper.findAll('.video-actions button').map((button) => button.attributes('aria-pressed'))).toEqual(['false', 'false'])
  })

  it('retries the list request after a loading failure', async () => {
    mocks.get
      .mockRejectedValueOnce(new Error('list failed'))
      .mockResolvedValueOnce({ data: [video('A'), video('B', 'retried recommendation')] })
    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    await wrapper.get('.error-state button').trigger('click')
    await flushPromises()

    expect(mocks.get.mock.calls.map(([url]) => url)).toEqual(['/api/videos', '/api/videos'])
    expect(wrapper.text()).toContain('retried recommendation')
  })
})
