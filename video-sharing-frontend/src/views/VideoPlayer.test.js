import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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

function deferred() {
  let resolve
  let reject
  const promise = new Promise((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, reject, resolve }
}

function video(id, title = `video-${id}`) {
  return {
    id,
    title,
    filePath: `${id}.mp4`,
    thumbnailPath: `${id}.jpg`,
    views: 12,
    createdAt: '2026-07-20T00:00:00.000Z',
    description: `${title} description`,
    uploader: `${title} safe author`,
  }
}

describe('VideoPlayer', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.route.params = reactive({ id: 'A' })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('keeps the newer A response visible when the first A request fails after A to B to A navigation', async () => {
    const firstAVideo = deferred()
    const firstAList = deferred()
    const bVideo = deferred()
    const bList = deferred()
    const secondAVideo = deferred()
    const secondAList = deferred()
    mocks.get
      .mockImplementationOnce(() => firstAVideo.promise)
      .mockImplementationOnce(() => firstAList.promise)
      .mockImplementationOnce(() => bVideo.promise)
      .mockImplementationOnce(() => bList.promise)
      .mockImplementationOnce(() => secondAVideo.promise)
      .mockImplementationOnce(() => secondAList.promise)

    const wrapper = mount(VideoPlayer, { global })
    expect(mocks.get).toHaveBeenCalledTimes(2)

    mocks.route.params.id = 'B'
    await nextTick()
    expect(mocks.get).toHaveBeenCalledTimes(4)

    mocks.route.params.id = 'A'
    await nextTick()
    expect(mocks.get).toHaveBeenCalledTimes(6)

    secondAVideo.resolve({ data: video('A', 'A2 current video') })
    secondAList.resolve({ data: [video('A'), video('recommended', 'A2 recommendation')] })
    await flushPromises()
    expect(wrapper.text()).toContain('A2 current video')

    firstAVideo.reject(new Error('stale A request failed'))
    await flushPromises()

    expect(wrapper.text()).toContain('A2 current video')
    expect(wrapper.find('.error-state').exists()).toBe(false)
  })

  it('loads a fresh page on route id change and resets local like and save state', async () => {
    mocks.get.mockImplementation((url) => {
      if (url.startsWith('/api/videos/')) return Promise.resolve({ data: video(url.split('/').pop()) })
      return Promise.resolve({ data: [video('A'), video('B')] })
    })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    const actionButtons = wrapper.findAll('.video-actions button')
    await actionButtons[0].trigger('click')
    await actionButtons[1].trigger('click')
    expect(actionButtons[0].attributes('aria-pressed')).toBe('true')
    expect(actionButtons[1].attributes('aria-pressed')).toBe('true')

    mocks.route.params.id = 'B'
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/videos/B')
    expect(mocks.get).toHaveBeenCalledTimes(4)
    expect(wrapper.findAll('.video-actions button').map((button) => button.attributes('aria-pressed'))).toEqual(['false', 'false'])
  })

  it('clearly explains that like and watch-later actions only last on this page visit', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: video('A', 'current video') })
      .mockResolvedValueOnce({ data: [video('B')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.get('.local-action-note').text()).toContain('仅在当前页面和本次浏览中有效')
  })

  it('displays the safe DTO uploader as the current video author', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: video('A', 'current video') })
      .mockResolvedValueOnce({ data: [video('B')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.get('.author-panel__name').text()).toBe('current video safe author')
  })

  it('shows the full page error state when the current video request fails', async () => {
    mocks.get
      .mockRejectedValueOnce(new Error('current video failed'))
      .mockResolvedValueOnce({ data: [video('recommended')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.find('.error-state').exists()).toBe(true)
    expect(wrapper.find('.watch-layout').exists()).toBe(false)
  })

  it('keeps the current video visible and shows a local notice when recommendations fail', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: video('A', 'current video') })
      .mockRejectedValueOnce(new Error('recommendations failed'))

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('current video')
    expect(wrapper.find('.error-state').exists()).toBe(false)
    expect(wrapper.find('.recommendations-error').exists()).toBe(true)
  })

  it('retries only the recommendation area after recommendation loading fails', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: video('A', 'current video') })
      .mockRejectedValueOnce(new Error('recommendations failed'))
      .mockResolvedValueOnce({ data: [video('A'), video('B', 'retried recommendation')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    await wrapper.get('.recommendations-retry').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledTimes(3)
    expect(mocks.get.mock.calls.map(([url]) => url)).toEqual(['/api/videos/A', '/api/videos', '/api/videos'])
    expect(wrapper.text()).toContain('retried recommendation')
    expect(wrapper.text()).toContain('current video')
  })

  it('ignores a stale recommendation success after navigating to another video', async () => {
    const firstVideo = deferred()
    const staleList = deferred()
    const secondVideo = deferred()
    const currentList = deferred()
    mocks.get
      .mockImplementationOnce(() => firstVideo.promise)
      .mockImplementationOnce(() => staleList.promise)
      .mockImplementationOnce(() => secondVideo.promise)
      .mockImplementationOnce(() => currentList.promise)

    const wrapper = mount(VideoPlayer, { global })
    mocks.route.params.id = 'B'
    await nextTick()
    secondVideo.resolve({ data: video('B', 'current B') })
    currentList.resolve({ data: [video('B'), video('C', 'current recommendation')] })
    await flushPromises()

    staleList.resolve({ data: [video('A'), video('D', 'stale recommendation')] })
    firstVideo.resolve({ data: video('A', 'stale A') })
    await flushPromises()

    expect(wrapper.text()).toContain('current recommendation')
    expect(wrapper.text()).not.toContain('stale recommendation')
  })
})
