import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  rawPut: vi.fn(),
  generateThumbnailFromVideo: vi.fn(),
}))

vi.mock('../services/api', () => ({ default: { get: mocks.get, post: mocks.post } }))
vi.mock('axios', () => ({ default: { put: mocks.rawPut } }))
vi.mock('../utils/videoThumbnail', () => ({
  generateThumbnailFromVideo: mocks.generateThumbnailFromVideo,
}))

import VideoUpload from './VideoUpload.vue'

describe('upload moderation messaging', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
    mocks.rawPut.mockReset()
    mocks.generateThumbnailFromVideo.mockReset()
    mocks.generateThumbnailFromVideo.mockResolvedValue(new File(['cover'], 'auto-cover.jpg', { type: 'image/jpeg' }))
    mocks.rawPut.mockResolvedValue({})
    mocks.get.mockImplementation((url) => {
      if (url === '/api/categories') return Promise.resolve({ data: [{ id: 1, code: 'movie', name: '电影' }] })
      if (url === '/api/categories/1/tags') return Promise.resolve({ data: [] })
      return Promise.resolve({ data: [] })
    })
  })

  it('says upload is pending review and points to the user dashboard', async () => {
    mocks.post
      .mockResolvedValueOnce({
        data: {
          uploadSessionId: 'session-id',
          video: {
            key: 'pending/users/7/session-id/video/demo.mp4',
            uploadUrl: 'http://minio.local/upload-video',
            contentType: 'video/mp4',
          },
          thumbnail: {
            key: 'pending/users/7/session-id/thumbnail/auto-cover.jpg',
            uploadUrl: 'http://minio.local/upload-thumb',
            contentType: 'image/jpeg',
          },
        },
      })
      .mockResolvedValueOnce({ data: { id: 1, status: 'PENDING' } })
    const wrapper = mount(VideoUpload, {
      global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } },
    })
    const file = new File(['content'], 'demo.mp4', { type: 'video/mp4' })
    const input = wrapper.get('#file')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })

    await flushPromises()
    await wrapper.get('#title').setValue('My video')
    await wrapper.get('#category').setValue('1')
    await flushPromises()
    await input.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="status"]').exists()).toBe(true)
    expect(wrapper.get('a[href="/dashboard"]').exists()).toBe(true)
    expect(mocks.post).toHaveBeenCalledWith('/api/videos/upload-complete', expect.any(Object))
  })
})
