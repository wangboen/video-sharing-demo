import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  observer: null,
}))

vi.mock('../services/api', () => ({
  default: { get: mocks.get },
}))

const global = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
    LoadingState: { template: '<div class="loading-state-stub">loading</div>' },
    ErrorState: { props: ['message'], template: '<div class="error-state"><span>{{ message }}</span><button @click="$emit(\'retry\')">retry</button></div>' },
  },
}

import WatchHistoryView from './WatchHistoryView.vue'

function record(
  id,
  title = `clip-${id}`,
  thumbnailUrl = `http://cdn.test/thumb-${id}.jpg`,
  lastWatchedAt = '2026-08-03T08:00:00Z',
) {
  return {
    video: { id, title, thumbnailUrl, uploadDate: '2026-08-01T08:00:00Z' },
    progressPercent: 45,
    lastWatchedAt,
  }
}

describe('WatchHistoryView', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.observer = null
    globalThis.IntersectionObserver = class {
      constructor(callback) {
        this.callback = callback
        this.observe = vi.fn()
        this.disconnect = vi.fn()
        mocks.observer = this
      }
    }
  })

  it('loads the first page and links records to video pages', async () => {
    mocks.get.mockResolvedValue({
      data: { content: [record(88, 'first history')], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/users/me/watch-history', {
      params: { page: 0, size: 20 },
    })
    expect(wrapper.text()).toContain('first history')
    expect(wrapper.get('.watch-history-record').attributes('href')).toBe('/video/88')
  })

  it('renders thumbnail images and placeholders in history records', async () => {
    mocks.get.mockResolvedValue({
      data: { content: [
        record(88, 'with thumbnail', 'http://cdn.test/history.jpg'),
        record(89, 'without thumbnail', null),
      ], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()

    const image = wrapper.get('.watch-history-thumb img')
    expect(image.attributes('src')).toBe('http://cdn.test/history.jpg')
    expect(image.attributes('alt')).toBe('with thumbnail')
    expect(image.attributes('loading')).toBe('lazy')
    expect(wrapper.get('.watch-history-placeholder').exists()).toBe(true)
  })

  it('shows timeline labels for watched time ranges without repeating consecutive labels', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-15T12:00:00+08:00'))
    mocks.get.mockResolvedValue({
      data: { content: [
        record(1, 'today clip', null, '2026-08-15T08:00:00+08:00'),
        record(2, 'yesterday clip', null, '2026-08-14T08:00:00+08:00'),
        record(3, 'before yesterday clip', null, '2026-08-13T08:00:00+08:00'),
        record(4, 'week clip one', null, '2026-08-10T08:00:00+08:00'),
        record(5, 'week clip two', null, '2026-08-09T08:00:00+08:00'),
        record(6, 'month clip', null, '2026-08-01T08:00:00+08:00'),
        record(7, 'older clip', null, '2026-07-31T08:00:00+08:00'),
      ], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()

    const labels = wrapper.findAll('[data-testid="history-timeline-label"]').map((label) => label.text())
    expect(labels).toEqual(['今天', '昨天', '前天', '一周内', '本月', '一月前'])
    vi.useRealTimers()
  })

  it('renders the timeline outside the clickable history record cards', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-15T12:00:00+08:00'))
    mocks.get.mockResolvedValue({
      data: { content: [record(1, 'today clip', null, '2026-08-15T08:00:00+08:00')], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()

    const group = wrapper.get('[data-testid="history-timeline-group"]')
    expect(group.get('[data-testid="history-timeline-label"]').element.closest('a')).toBe(null)
    expect(wrapper.get('.watch-history-record').find('.watch-history-timeline').exists()).toBe(false)
    vi.useRealTimers()
  })

  it('centers one timeline label across all records in the same time range', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-15T12:00:00+08:00'))
    mocks.get.mockResolvedValue({
      data: { content: [
        record(1, 'week clip one', null, '2026-08-10T08:00:00+08:00'),
        record(2, 'week clip two', null, '2026-08-09T08:00:00+08:00'),
      ], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()

    const group = wrapper.get('[data-testid="history-timeline-group"]')
    expect(group.findAll('.watch-history-record')).toHaveLength(2)
    expect(group.findAll('[data-testid="history-timeline-label"]')).toHaveLength(1)
    expect(group.get('.watch-history-timeline').classes()).toContain('watch-history-timeline--grouped')
    vi.useRealTimers()
  })

  it('keeps filters collapsed until the user expands them', async () => {
    mocks.get.mockResolvedValue({
      data: { content: [record(1, 'filtered')], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()

    expect(wrapper.find('.watch-history-filters').exists()).toBe(false)
    await wrapper.get('[data-testid="watch-history-filter-toggle"]').trigger('click')
    expect(wrapper.find('.watch-history-filters').exists()).toBe(true)
  })

  it('opens the native date picker when clicking the whole date control', async () => {
    HTMLInputElement.prototype.showPicker = vi.fn()
    const showPicker = vi.spyOn(HTMLInputElement.prototype, 'showPicker')
    mocks.get.mockResolvedValue({
      data: { content: [record(1, 'filtered')], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()
    await wrapper.get('[data-testid="watch-history-filter-toggle"]').trigger('click')
    await wrapper.get('[data-testid="watch-history-date-control"]').trigger('click')

    expect(showPicker).toHaveBeenCalledTimes(1)
    showPicker.mockRestore()
  })

  it('loads the next page when the bottom sentinel is visible', async () => {
    mocks.get
      .mockResolvedValueOnce({ data: { content: [record(1, 'page one')], number: 0, totalPages: 2 } })
      .mockResolvedValueOnce({ data: { content: [record(2, 'page two')], number: 1, totalPages: 2 } })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()
    mocks.observer.callback([{ isIntersecting: true }])
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(2, '/api/users/me/watch-history', {
      params: { page: 1, size: 20 },
    })
    expect(wrapper.text()).toContain('page one')
    expect(wrapper.text()).toContain('page two')
  })

  it('resets the list when applying inclusive cutoff quick filters and clearing filters', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-03T12:00:00+08:00'))
    mocks.get.mockResolvedValue({
      data: { content: [record(1, 'filtered')], number: 0, totalPages: 1 },
    })

    const wrapper = mount(WatchHistoryView, { global })
    await flushPromises()
    expect(wrapper.find('[data-testid="filter-today"]').exists()).toBe(false)
    await wrapper.get('[data-testid="watch-history-filter-toggle"]').trigger('click')
    await wrapper.get('[data-testid="filter-yesterday"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="filter-before-yesterday"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="filter-last-week"]').trigger('click')
    await flushPromises()
    await wrapper.get('.watch-history-clear').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenNthCalledWith(2, '/api/users/me/watch-history', {
      params: { page: 0, size: 20, to: '2026-08-02' },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(3, '/api/users/me/watch-history', {
      params: { page: 0, size: 20, to: '2026-08-01' },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(4, '/api/users/me/watch-history', {
      params: { page: 0, size: 20, to: '2026-07-27' },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(5, '/api/users/me/watch-history', {
      params: { page: 0, size: 20 },
    })
    vi.useRealTimers()
  })
})
