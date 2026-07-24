import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('../services/api', () => ({ default: { post: mocks.post } }))

import VideoUpload from './VideoUpload.vue'

describe('upload moderation messaging', () => {
  it('says upload is pending review and points to the user dashboard', async () => {
    mocks.post.mockResolvedValue({ data: { id: 1, status: 'PENDING' } })
    const wrapper = mount(VideoUpload, {
      global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } },
    })
    const file = new File(['content'], 'demo.mp4', { type: 'video/mp4' })
    const input = wrapper.get('#file')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })

    await wrapper.get('#title').setValue('My video')
    await input.trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('视频已提交审核')
    expect(wrapper.get('a[href="/dashboard"]').text()).toContain('查看我的投稿')
  })
})
