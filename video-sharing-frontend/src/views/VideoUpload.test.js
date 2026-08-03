import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  apiPut: vi.fn(),
  rawPut: vi.fn(),
  generateThumbnailFromVideo: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get, post: mocks.post, put: mocks.apiPut },
}))

vi.mock('axios', () => ({
  default: { put: mocks.rawPut },
}))

vi.mock('../utils/videoThumbnail', () => ({
  generateThumbnailFromVideo: mocks.generateThumbnailFromVideo,
}))

import VideoUpload from './VideoUpload.vue'

function uploadedFile(name, type) {
  return new File(['content'], name, { type })
}

function sessionResponse(thumbnail = null) {
  return {
    data: {
      uploadSessionId: 'session-id',
      video: {
        key: 'pending/users/7/session-id/video/demo.mp4',
        uploadUrl: 'http://minio.local/upload-video',
        contentType: 'video/mp4',
      },
      thumbnail,
    },
  }
}

function deferred() {
  let resolve
  const promise = new Promise((promiseResolve) => {
    resolve = promiseResolve
  })
  return { promise, resolve }
}

async function selectFile(wrapper, selector, file) {
  const input = wrapper.get(selector)
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  Object.defineProperty(input.element, 'value', { configurable: true, writable: true, value: `C:\\fakepath\\${file.name}` })
  await input.trigger('change')
}

async function chooseCategory(wrapper) {
  await wrapper.get('#category').setValue('1')
  await flushPromises()
}

describe('video direct upload', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
    mocks.apiPut.mockReset()
    mocks.rawPut.mockReset()
    mocks.generateThumbnailFromVideo.mockReset()
    mocks.generateThumbnailFromVideo.mockResolvedValue(uploadedFile('auto-cover.jpg', 'image/jpeg'))
    mocks.get.mockImplementation((url) => {
      if (url === '/api/categories') {
        return Promise.resolve({ data: [{ id: 1, code: 'movie', name: '电影' }] })
      }
      if (url === '/api/categories/1/tags') {
        return Promise.resolve({ data: [
          { id: 10, code: 'movie_action', name: '动作' },
          { id: 11, code: 'movie_review', name: '影评' },
        ] })
      }
      return Promise.resolve({ data: [] })
    })
    URL.createObjectURL = vi.fn(() => 'blob:thumbnail')
    URL.revokeObjectURL = vi.fn()
  })

  it('creates an upload session, uploads the video to MinIO, then completes the submission', async () => {
    const thumbnailTarget = {
      key: 'pending/users/7/session-id/thumbnail/auto-cover.jpg',
      uploadUrl: 'http://minio.local/upload-auto-thumb',
      contentType: 'image/jpeg',
    }
    mocks.post
      .mockResolvedValueOnce(sessionResponse(thumbnailTarget))
      .mockResolvedValueOnce({ data: { id: 1 } })
    mocks.rawPut.mockResolvedValue({})
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    await flushPromises()

    await wrapper.get('#title').setValue('My video')
    await wrapper.get('#description').setValue('Description')
    await chooseCategory(wrapper)
    await wrapper.get('input[value="10"]').setValue(true)
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.generateThumbnailFromVideo).toHaveBeenCalledWith(video)
    expect(mocks.post).toHaveBeenNthCalledWith(1, '/api/videos/upload-session', {
      title: 'My video',
      description: 'Description',
      video: { fileName: 'demo.mp4', contentType: 'video/mp4', size: video.size },
      thumbnail: { fileName: 'auto-cover.jpg', contentType: 'image/jpeg', size: expect.any(Number) },
    })
    expect(mocks.rawPut).toHaveBeenCalledWith('http://minio.local/upload-video', video, {
      headers: { 'Content-Type': 'video/mp4' },
    })
    expect(mocks.rawPut).toHaveBeenCalledWith('http://minio.local/upload-auto-thumb', expect.any(File), {
      headers: { 'Content-Type': 'image/jpeg' },
    })
    expect(mocks.apiPut).not.toHaveBeenCalled()
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/api/videos/upload-complete', {
      uploadSessionId: 'session-id',
      videoKey: 'pending/users/7/session-id/video/demo.mp4',
      thumbnailKey: thumbnailTarget.key,
      title: 'My video',
      description: 'Description',
      categoryId: 1,
      tagIds: [10],
    })
  })

  it('requires a category before starting upload', async () => {
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    await flushPromises()

    await wrapper.get('#title').setValue('My video')
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.post).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('分类')
  })

  it('uploads the thumbnail object when a thumbnail is selected', async () => {
    const thumbnailTarget = {
      key: 'pending/users/7/session-id/thumbnail/cover.png',
      uploadUrl: 'http://minio.local/upload-thumb',
      contentType: 'image/png',
    }
    mocks.post
      .mockResolvedValueOnce(sessionResponse(thumbnailTarget))
      .mockResolvedValueOnce({ data: { id: 1 } })
    mocks.rawPut.mockResolvedValue({})
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    const thumbnail = uploadedFile('cover.png', 'image/png')
    await flushPromises()

    await wrapper.get('#title').setValue('My video')
    await chooseCategory(wrapper)
    await selectFile(wrapper, '#file', video)
    await selectFile(wrapper, '#thumbnail', thumbnail)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.generateThumbnailFromVideo).not.toHaveBeenCalled()
    expect(mocks.rawPut).toHaveBeenNthCalledWith(1, 'http://minio.local/upload-video', video, expect.any(Object))
    expect(mocks.rawPut).toHaveBeenNthCalledWith(2, 'http://minio.local/upload-thumb', thumbnail, {
      headers: { 'Content-Type': 'image/png' },
    })
    expect(mocks.post.mock.calls[1][1].thumbnailKey).toBe(thumbnailTarget.key)
  })

  it('does not complete the submission when direct upload fails', async () => {
    mocks.post
      .mockResolvedValueOnce(sessionResponse())
      .mockResolvedValueOnce({})
    mocks.rawPut.mockRejectedValueOnce(new Error('minio failed'))
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    await flushPromises()

    await wrapper.get('#title').setValue('My video')
    await chooseCategory(wrapper)
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledTimes(2)
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/api/videos/upload-cancel', {
      uploadSessionId: 'session-id',
      videoKey: 'pending/users/7/session-id/video/demo.mp4',
      thumbnailKey: null,
    })
    expect(wrapper.get('[role="alert"]').text()).not.toContain('minio failed')
  })

  it('cancels uploaded objects when thumbnail direct upload fails', async () => {
    const thumbnailTarget = {
      key: 'pending/users/7/session-id/thumbnail/cover.png',
      uploadUrl: 'http://minio.local/upload-thumb',
      contentType: 'image/png',
    }
    mocks.post
      .mockResolvedValueOnce(sessionResponse(thumbnailTarget))
      .mockResolvedValueOnce({})
    mocks.rawPut
      .mockResolvedValueOnce({})
      .mockRejectedValueOnce(new Error('thumbnail upload failed'))
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    const thumbnail = uploadedFile('cover.png', 'image/png')
    await flushPromises()

    await wrapper.get('#title').setValue('My video')
    await chooseCategory(wrapper)
    await selectFile(wrapper, '#file', video)
    await selectFile(wrapper, '#thumbnail', thumbnail)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledTimes(2)
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/api/videos/upload-cancel', {
      uploadSessionId: 'session-id',
      videoKey: 'pending/users/7/session-id/video/demo.mp4',
      thumbnailKey: thumbnailTarget.key,
    })
  })

  it('prevents duplicate submits while the upload is running', async () => {
    const request = deferred()
    mocks.post.mockReturnValue(request.promise)
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    await flushPromises()

    await wrapper.get('#title').setValue('My video')
    await chooseCategory(wrapper)
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    request.resolve(sessionResponse())
    await flushPromises()
  })

  it('clears native file inputs after success so the same files can be selected again', async () => {
    mocks.post
      .mockResolvedValueOnce(sessionResponse())
      .mockResolvedValueOnce({ data: { id: 1 } })
    mocks.rawPut.mockResolvedValue({})
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    const thumbnail = uploadedFile('cover.png', 'image/png')
    await flushPromises()

    await wrapper.get('#title').setValue('My video')
    await chooseCategory(wrapper)
    await selectFile(wrapper, '#file', video)
    await selectFile(wrapper, '#thumbnail', thumbnail)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('#file').element.value).toBe('')
    expect(wrapper.get('#thumbnail').element.value).toBe('')
  })
})
