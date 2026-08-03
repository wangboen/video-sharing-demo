import { flushPromises, mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  push: vi.fn(),
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get },
}))

vi.mock('vue-router', () => ({
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
  useRouter: () => ({ push: mocks.push }),
}))

import AppHeader from './AppHeader.vue'

function record(id, title = `history-${id}`, thumbnailUrl = `http://cdn.test/thumb-${id}.jpg`) {
  return {
    video: { id, title, thumbnailUrl, uploadDate: '2026-08-01T08:00:00Z' },
    progressPercent: 45,
    lastWatchedAt: '2026-08-03T08:00:00Z',
  }
}

describe('AppHeader watch history dropdown', () => {
  beforeEach(() => {
    vi.useRealTimers()
    mocks.get.mockReset()
    mocks.push.mockReset()
  })

  it('shows no watch history entry for signed-out visitors', () => {
    const wrapper = mount(AppHeader, {
      props: { user: null, sidebarCollapsed: false },
    })

    expect(wrapper.find('.app-header__history').exists()).toBe(false)
  })

  it('loads recent watch history on hover and links to video pages', async () => {
    mocks.get.mockResolvedValue({ data: { content: [record(88, 'continue clip')] } })
    const wrapper = mount(AppHeader, {
      props: { user: { username: 'viewer' }, sidebarCollapsed: false },
    })

    await wrapper.get('.app-header__history').trigger('mouseenter')
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/users/me/watch-history', {
      params: { page: 0, size: 10 },
    })
    expect(wrapper.text()).toContain('continue clip')
    expect(wrapper.get('.app-header__history-record').attributes('href')).toBe('/video/88')
    expect(wrapper.get('.app-header__history-all').attributes('href')).toBe('/watch-history')
  })

  it('renders thumbnail images and a placeholder for records without thumbnails', async () => {
    mocks.get.mockResolvedValue({ data: { content: [
      record(88, 'with thumbnail', 'http://cdn.test/with.jpg'),
      record(89, 'without thumbnail', null),
    ] } })
    const wrapper = mount(AppHeader, {
      props: { user: { username: 'viewer' }, sidebarCollapsed: false },
    })

    await wrapper.get('.app-header__history').trigger('mouseenter')
    await flushPromises()

    const image = wrapper.get('.app-header__history-thumb img')
    expect(image.attributes('src')).toBe('http://cdn.test/with.jpg')
    expect(image.attributes('alt')).toBe('with thumbnail')
    expect(wrapper.get('.app-header__history-placeholder').exists()).toBe(true)
  })

  it('reuses recent watch history for quick hover returns', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-03T08:00:00Z'))
    mocks.get
      .mockResolvedValueOnce({ data: { content: [record(88, 'old history')] } })
      .mockResolvedValueOnce({ data: { content: [record(89, 'fresh history')] } })
    const wrapper = mount(AppHeader, {
      props: { user: { username: 'viewer' }, sidebarCollapsed: false },
    })

    await wrapper.get('.app-header__history').trigger('mouseenter')
    await flushPromises()
    expect(wrapper.text()).toContain('old history')

    await wrapper.get('.app-header__history').trigger('mouseleave')
    vi.setSystemTime(new Date('2026-08-03T08:00:10Z'))
    await wrapper.get('.app-header__history').trigger('mouseenter')
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('old history')
  })

  it('keeps recent watch history cached until the refresh cooldown finishes', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-03T08:00:00Z'))
    mocks.get
      .mockResolvedValueOnce({ data: { content: [record(88, 'old history')] } })
      .mockResolvedValueOnce({ data: { content: [record(89, 'fresh history')] } })
    const wrapper = mount(AppHeader, {
      props: { user: { username: 'viewer' }, sidebarCollapsed: false },
    })

    await wrapper.get('.app-header__history').trigger('mouseenter')
    await flushPromises()
    expect(wrapper.text()).toContain('old history')

    await wrapper.get('.app-header__history').trigger('mouseleave')
    vi.setSystemTime(new Date('2026-08-03T08:00:31Z'))
    await wrapper.get('.app-header__history').trigger('mouseenter')
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('old history')

    await wrapper.get('.app-header__history').trigger('mouseleave')
    vi.advanceTimersByTime(30_000)
    await wrapper.get('.app-header__history').trigger('mouseenter')
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('fresh history')
    expect(wrapper.text()).not.toContain('old history')
  })

  it('keeps the dropdown connected to its hover trigger', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/components/AppHeader.vue'), 'utf8')

    expect(source).toContain('top: 100%')
    expect(source).not.toContain('top: calc(100% + 10px)')
  })
})
