import { flushPromises, mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
  getAuthToken: vi.fn(),
  getStoredUser: vi.fn(),
  route: { params: { id: 'A' } },
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get, post: mocks.post, delete: mocks.delete },
}))

vi.mock('../services/auth', () => ({
  getAuthToken: mocks.getAuthToken,
  getStoredUser: mocks.getStoredUser,
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
    videoUrl: `http://minio.local/video-sharing/public/videos/${id}.mp4`,
    thumbnailUrl: `http://minio.local/video-sharing/public/thumbnails/${id}.jpg`,
    views: 12,
    uploadDate: '2026-07-20T00:00:00.000Z',
    description: `${title} description`,
    uploader: `${title} safe author`,
  }
}

describe('VideoPlayer', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
    mocks.delete.mockReset()
    mocks.getAuthToken.mockReset()
    mocks.getAuthToken.mockReturnValue('token-1')
    mocks.getStoredUser.mockReturnValue({ username: 'vision-user' })
    mocks.route.params = reactive({ id: 'A' })
  })

  it('loads the current video and recommendations from the list endpoint only', async () => {
    mocks.get.mockResolvedValue({ data: [video('A', 'current video'), video('B', 'recommended video')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(mocks.get.mock.calls.map(([url]) => url)).toEqual([
      '/api/videos',
      '/api/videos/A/comments',
      '/api/users/me/watch-history/A',
    ])
    expect(wrapper.text()).toContain('current video')
    expect(wrapper.text()).toContain('recommended video')
  })

  it('displays the safe DTO uploader as the current video author', async () => {
    mocks.get.mockResolvedValue({ data: [video('A', 'current video'), video('B')] })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.get('.author-panel__name').text()).toBe('current video safe author')
  })

  it('shows category and tags from the public video DTO', async () => {
    mocks.get.mockResolvedValue({
      data: [{
        ...video('A', 'current video'),
        category: { id: 1, code: 'movie', name: '电影' },
        tags: [{ id: 10, code: 'movie_action', name: '动作' }],
      }],
    })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.get('.video-taxonomy__category').text()).toBe('电影')
    expect(wrapper.get('.video-taxonomy__tag').text()).toBe('动作')
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

    expect(mocks.get.mock.calls.map(([url]) => url)).toEqual([
      '/api/videos',
      '/api/videos/A/comments',
      '/api/users/me/watch-history/A',
      '/api/videos',
      '/api/videos/B/comments',
      '/api/users/me/watch-history/B',
    ])
    expect(wrapper.findAll('.video-actions button').map((button) => button.attributes('aria-pressed'))).toEqual(['false', 'false'])
  })

  it('loads comments for a new route while the previous video comments are still pending', async () => {
    let resolveFirstComments
    const firstComments = new Promise((resolve) => {
      resolveFirstComments = resolve
    })
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A'), video('B')] })
      if (url === '/api/videos/A/comments') return firstComments
      return Promise.resolve({
        data: { featured: [], comments: [{ commentId: 2, username: 'B-user', content: 'B comment' }], nextCursor: null },
      })
    })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    mocks.route.params.id = 'B'
    await nextTick()
    await flushPromises()

    expect(mocks.get.mock.calls.map(([url]) => url)).toContain('/api/videos/B/comments')
    expect(wrapper.text()).toContain('B comment')
    resolveFirstComments({ data: { featured: [], comments: [{ commentId: 1, content: 'A comment' }], nextCursor: null } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('A comment')
  })

  it('retries the list request after a loading failure', async () => {
    mocks.get
      .mockRejectedValueOnce(new Error('list failed'))
      .mockResolvedValueOnce({ data: [video('A'), video('B', 'retried recommendation')] })
    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    await wrapper.get('.error-state button').trigger('click')
    await flushPromises()

    expect(mocks.get.mock.calls.map(([url]) => url)).toEqual([
      '/api/videos',
      '/api/videos',
      '/api/videos/A/comments',
      '/api/users/me/watch-history/A',
    ])
    expect(wrapper.text()).toContain('retried recommendation')
  })

  it('renders featured comments above normal comments and labels anonymized authors', async () => {
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A')] })
      return Promise.resolve({
        data: {
          featured: [{ commentId: 1, username: 'top-user', content: 'featured comment', likeCount: 4 }],
          comments: [{ commentId: 2, username: 'hidden-user', content: 'normal comment', anonymized: true, likeCount: 0 }],
          nextCursor: null,
        },
      })
    })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    const items = wrapper.findAll('.comment-item')
    expect(items.map((item) => item.text())).toEqual([
      expect.stringContaining('featured comment'),
      expect.stringContaining('匿名用户'),
    ])
    expect(wrapper.text()).toContain('normal comment')
  })

  it('adds a sending comment after the publish request is accepted', async () => {
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A')] })
      return Promise.resolve({ data: { featured: [], comments: [], nextCursor: null } })
    })
    mocks.post.mockResolvedValue({ data: { commentId: 99, requestId: 'request-99', status: 'ACCEPTED' } })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    await wrapper.get('.comment-composer textarea').setValue('pending comment')
    await wrapper.get('.comment-composer').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/api/videos/A/comments', { content: 'pending comment' })
    expect(wrapper.get('.comment-item--sending').text()).toContain('pending comment')
    expect(wrapper.get('.comment-item--sending').text()).toContain('发送中')
  })
  it('preserves string comment ids when sending like requests', async () => {
    const largeCommentId = '9007199254740993'
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A')] })
      return Promise.resolve({
        data: {
          featured: [],
          comments: [{ commentId: largeCommentId, username: 'safe-user', content: 'precise id', likeCount: 0, likedByMe: false }],
          nextCursor: null,
        },
      })
    })
    mocks.post.mockResolvedValue({})

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    await wrapper.get('.comment-item button').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith(`/api/comments/${largeCommentId}/like`)
  })

  it('shows a friendly comment loading error from the backend message', async () => {
    const error = new Error('service unavailable')
    error.response = { status: 503, data: { message: 'Service is temporarily unavailable' } }
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A')] })
      return Promise.reject(error)
    })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('服务暂时不可用，请稍后重试。')
  })

  it('keeps the comment draft when publishing fails', async () => {
    const error = new Error('comment service unavailable')
    error.response = { status: 503, data: { message: 'Comment service is temporarily unavailable' } }
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A')] })
      return Promise.resolve({ data: { featured: [], comments: [], nextCursor: null } })
    })
    mocks.post.mockRejectedValue(error)

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    await wrapper.get('.comment-composer textarea').setValue('retry me')
    await wrapper.get('.comment-composer').trigger('submit')
    await flushPromises()

    expect(wrapper.get('.comment-composer textarea').element.value).toBe('retry me')
    expect(wrapper.text()).toContain('评论服务暂时不可用，请稍后重试。')
  })

  it('restores the last watch position for a signed-in viewer', async () => {
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A')] })
      if (url === '/api/users/me/watch-history/A') {
        return Promise.resolve({ data: { lastPositionSeconds: 38, completed: false } })
      }
      return Promise.resolve({ data: { featured: [], comments: [], nextCursor: null } })
    })

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    await nextTick()

    expect(wrapper.get('video').element.currentTime).toBe(38)
  })

  it('reports watch progress after meaningful playback', async () => {
    mocks.get.mockImplementation((url) => {
      if (url === '/api/videos') return Promise.resolve({ data: [video('A')] })
      if (url === '/api/users/me/watch-history/A') return Promise.resolve({ data: null })
      return Promise.resolve({ data: { featured: [], comments: [], nextCursor: null } })
    })
    mocks.post.mockResolvedValue({})

    const wrapper = mount(VideoPlayer, { global })
    await flushPromises()
    const player = wrapper.get('video').element
    Object.defineProperty(player, 'duration', { configurable: true, value: 100 })
    player.currentTime = 12
    await wrapper.get('video').trigger('timeupdate')

    expect(mocks.post).toHaveBeenCalledWith('/api/videos/A/watch-progress', {
      positionSeconds: 12,
      durationSeconds: 100,
      completed: false,
      eventType: 'heartbeat',
    })
  })
})
