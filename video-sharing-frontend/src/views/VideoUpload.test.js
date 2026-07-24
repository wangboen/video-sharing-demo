import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../services/api', () => ({
  default: { post: mocks.post },
}))

import VideoUpload from './VideoUpload.vue'

function uploadedFile(name, type) {
  return new File(['content'], name, { type })
}

function formDataValues(formData) {
  return Object.fromEntries(formData.entries())
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

describe('视频发布页', () => {
  beforeEach(() => {
    mocks.post.mockReset()
    URL.createObjectURL = vi.fn(() => 'blob:thumbnail')
    URL.revokeObjectURL = vi.fn()
  })

  it('提交视频时保留 title、description 和 file FormData 字段', async () => {
    mocks.post.mockResolvedValue({ data: { id: 1 } })
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')

    await wrapper.get('#title').setValue('我的视频')
    await wrapper.get('#description').setValue('视频简介')
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/api/videos/upload', expect.any(FormData), expect.any(Object))
    expect(formDataValues(mocks.post.mock.calls[0][1])).toMatchObject({
      title: '我的视频',
      description: '视频简介',
      file: video,
    })
  })

  it('选择封面时在 FormData 中包含 thumbnail', async () => {
    mocks.post.mockResolvedValue({ data: { id: 1 } })
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    const thumbnail = uploadedFile('cover.png', 'image/png')

    await wrapper.get('#title').setValue('我的视频')
    await selectFile(wrapper, '#file', video)
    await selectFile(wrapper, '#thumbnail', thumbnail)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(formDataValues(mocks.post.mock.calls[0][1]).thumbnail).toBe(thumbnail)
  })

  it('发布成功后显示视频列表入口', async () => {
    mocks.post.mockResolvedValue({ data: { id: 1 } })
    const wrapper = mount(VideoUpload, {
      global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } },
    })
    const video = uploadedFile('demo.mp4', 'video/mp4')

    await wrapper.get('#title').setValue('我的视频')
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('视频已提交审核')
    expect(wrapper.get('a[href="/dashboard"]').text()).toContain('查看我的投稿')
  })

  it('发布请求进行中时不会重复提交', async () => {
    const request = deferred()
    mocks.post.mockReturnValue(request.promise)
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')

    await wrapper.get('#title').setValue('我的视频')
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()

    request.resolve({ data: { id: 1 } })
    await flushPromises()
  })

  it('发布成功后清空原生文件控件，并允许再次选择同一视频发布', async () => {
    mocks.post.mockResolvedValue({ data: { id: 1 } })
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')
    const thumbnail = uploadedFile('cover.png', 'image/png')

    await wrapper.get('#title').setValue('我的视频')
    await selectFile(wrapper, '#file', video)
    await selectFile(wrapper, '#thumbnail', thumbnail)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('#file').element.value).toBe('')
    expect(wrapper.get('#thumbnail').element.value).toBe('')

    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledTimes(2)
    expect(formDataValues(mocks.post.mock.calls[1][1]).file).toBe(video)
  })

  it('发布失败后重新启用按钮并允许再次提交', async () => {
    mocks.post
      .mockRejectedValueOnce(new Error('upload failed'))
      .mockResolvedValueOnce({ data: { id: 1 } })
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')

    await wrapper.get('#title').setValue('我的视频')
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledTimes(2)
  })

  it('把后端英文或对象错误包装为稳定中文文案', async () => {
    mocks.post.mockRejectedValue({ response: { status: 500, data: { message: 'Failed to upload video: disk error' } } })
    const wrapper = mount(VideoUpload)
    const video = uploadedFile('demo.mp4', 'video/mp4')

    await wrapper.get('#title').setValue('我的视频')
    await selectFile(wrapper, '#file', video)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('服务暂时不可用，请稍后重试。')
    expect(wrapper.text()).not.toContain('Failed to upload video')
    expect(wrapper.text()).not.toContain('[object Object]')
  })
})
