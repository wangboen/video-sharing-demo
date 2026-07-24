import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
  createUrl: vi.fn(),
  revokeUrl: vi.fn(),
}))

vi.mock('../services/api', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    patch: mocks.patch,
    delete: mocks.delete,
  },
}))

import ModerationView from './ModerationView.vue'

const task = {
  id: 42,
  title: '海边延时摄影',
  description: '一段待审核的视频说明',
  uploader: 'camera-user',
  uploadDate: '2026-07-23T10:00:00',
  version: 4,
  claimedAt: '2026-07-24T08:00:00',
  leaseExpiresAt: '2026-07-24T08:15:00',
  hasThumbnail: true,
}

function mediaResponses() {
  mocks.get.mockImplementation((url) => Promise.resolve({
    data: new Blob([url], { type: url.endsWith('video') ? 'video/mp4' : 'image/jpeg' }),
  }))
}

async function mountClaimed(overrides = {}) {
  mocks.post.mockResolvedValueOnce({ status: 200, data: { ...task, ...overrides } })
  mediaResponses()
  const wrapper = mount(ModerationView)
  await wrapper.get('[data-test="claim"]').trigger('click')
  await flushPromises()
  return wrapper
}

describe('moderation claim workbench', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset())
    let urlIndex = 0
    mocks.createUrl.mockImplementation(() => `blob:moderation-${++urlIndex}`)
    vi.stubGlobal('URL', {
      createObjectURL: mocks.createUrl,
      revokeObjectURL: mocks.revokeUrl,
    })
  })

  it('claims once, renders full metadata, and loads authenticated Blob media', async () => {
    let resolveClaim
    mocks.post.mockReturnValueOnce(new Promise((resolve) => { resolveClaim = resolve }))
    mediaResponses()
    const wrapper = mount(ModerationView)

    await wrapper.get('[data-test="claim"]').trigger('click')
    await wrapper.get('[data-test="claim"]').trigger('click')
    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith('/api/moderation/videos/claim-next')

    resolveClaim({ status: 200, data: task })
    await flushPromises()

    expect(wrapper.text()).toContain(task.title)
    expect(wrapper.text()).toContain(task.description)
    expect(wrapper.text()).toContain(task.uploader)
    expect(wrapper.text()).toContain('版本 4')
    expect(wrapper.text()).toContain('领取时间')
    expect(wrapper.text()).toContain('租约到期')
    expect(mocks.get).toHaveBeenCalledWith(
      '/api/moderation/videos/42/preview/video',
      { responseType: 'blob' },
    )
    expect(mocks.get).toHaveBeenCalledWith(
      '/api/moderation/videos/42/preview/thumbnail',
      { responseType: 'blob' },
    )
    expect(wrapper.get('video').attributes('src')).toBe('blob:moderation-1')
    expect(wrapper.get('video').attributes('poster')).toBe('blob:moderation-2')
  })

  it('shows a directional empty state for 204 and accepts an existing lease response', async () => {
    mocks.post
      .mockResolvedValueOnce({ status: 204, data: '' })
      .mockResolvedValueOnce({ status: 200, data: task })
    mediaResponses()
    const wrapper = mount(ModerationView)

    await wrapper.get('[data-test="claim"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-test="empty"]').text()).toContain('当前没有待审核视频')
    expect(wrapper.get('[data-test="empty"]').text()).toContain('稍后再次领取')

    await wrapper.get('[data-test="claim"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain(task.title)
    expect(wrapper.text()).toContain('租约进行中')
  })

  it('does not request a thumbnail when the task has none and revokes every URL on unmount', async () => {
    const wrapper = await mountClaimed({ hasThumbnail: false })

    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(mocks.get.mock.calls[0][0]).toContain('/preview/video')

    wrapper.unmount()

    expect(mocks.revokeUrl).toHaveBeenCalledTimes(1)
    expect(mocks.revokeUrl).toHaveBeenCalledWith('blob:moderation-1')
  })

  it('heartbeats after 60 seconds, replaces version and expiry, and stops after unmount', async () => {
    vi.useFakeTimers()
    const wrapper = await mountClaimed()
    mocks.post.mockResolvedValueOnce({
      status: 200,
      data: { ...task, version: 5, leaseExpiresAt: '2026-07-24T08:16:00' },
    })

    await vi.advanceTimersByTimeAsync(60_000)
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/api/moderation/videos/42/heartbeat')
    expect(wrapper.text()).toContain('版本 5')
    expect(wrapper.text()).toContain('2026/07/24 08:16')

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(mocks.post).toHaveBeenCalledTimes(2)
  })

  it('clears lease media and timers when a heartbeat conflicts', async () => {
    vi.useFakeTimers()
    const wrapper = await mountClaimed()
    mocks.post.mockRejectedValueOnce({ response: { status: 409 } })

    await vi.advanceTimersByTimeAsync(60_000)
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('租约已发生变化')
    expect(wrapper.find('video').exists()).toBe(false)
    expect(mocks.revokeUrl).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(60_000)
    expect(mocks.post).toHaveBeenCalledTimes(2)
  })

  it('releases a held task and cleans up media', async () => {
    const wrapper = await mountClaimed()
    mocks.delete.mockResolvedValue({ status: 204 })

    await wrapper.get('[data-test="release"]').trigger('click')
    await flushPromises()

    expect(mocks.delete).toHaveBeenCalledWith('/api/moderation/videos/42/claim')
    expect(wrapper.find('video').exists()).toBe(false)
    expect(mocks.revokeUrl).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('任务已释放')
  })

  it('validates rejection reason and submits its trimmed value with the current version', async () => {
    const wrapper = await mountClaimed()

    await wrapper.get('[data-test="reject"]').trigger('click')
    expect(wrapper.get('[role="alert"]').text()).toContain('请输入驳回原因')
    expect(mocks.patch).not.toHaveBeenCalled()

    await wrapper.get('#reject-reason').setValue('  画面包含违规内容  ')
    mocks.patch.mockResolvedValue({ status: 200, data: {} })
    await wrapper.get('[data-test="reject"]').trigger('click')
    await flushPromises()

    expect(mocks.patch).toHaveBeenCalledWith('/api/moderation/videos/42', {
      decision: 'REJECT',
      reason: '画面包含违规内容',
      version: 4,
    })
    expect(wrapper.find('video').exists()).toBe(false)
    expect(wrapper.text()).toContain('审核决定已提交')
  })

  it('approves without a reason, prevents duplicate decisions, and cleans up on success', async () => {
    const wrapper = await mountClaimed()
    let resolveDecision
    mocks.patch.mockReturnValue(new Promise((resolve) => { resolveDecision = resolve }))

    await wrapper.get('[data-test="approve"]').trigger('click')
    await wrapper.get('[data-test="approve"]').trigger('click')

    expect(mocks.patch).toHaveBeenCalledTimes(1)
    expect(mocks.patch).toHaveBeenCalledWith('/api/moderation/videos/42', {
      decision: 'APPROVE',
      version: 4,
    })

    resolveDecision({ status: 200, data: {} })
    await flushPromises()
    expect(wrapper.find('video').exists()).toBe(false)
    expect(wrapper.get('[data-test="claim"]').attributes('disabled')).toBeUndefined()
  })

  it('recovers from a decision conflict but retains the task for retryable service failures', async () => {
    const wrapper = await mountClaimed()
    mocks.patch.mockRejectedValueOnce({ response: { status: 500 } })

    await wrapper.get('[data-test="approve"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('提交失败')
    expect(wrapper.find('video').exists()).toBe(true)

    mocks.patch.mockRejectedValueOnce({ response: { status: 409 } })
    await wrapper.get('[data-test="approve"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('任务已发生变化')
    expect(wrapper.find('video').exists()).toBe(false)
    expect(wrapper.get('[data-test="claim"]').attributes('disabled')).toBeUndefined()
  })

  it('rejects reasons longer than 500 characters without a request', async () => {
    const wrapper = await mountClaimed()
    await wrapper.get('#reject-reason').setValue('拒'.repeat(501))

    await wrapper.get('[data-test="reject"]').trigger('click')

    expect(wrapper.get('[role="alert"]').text()).toContain('不能超过 500 个字符')
    expect(mocks.patch).not.toHaveBeenCalled()
  })
})
