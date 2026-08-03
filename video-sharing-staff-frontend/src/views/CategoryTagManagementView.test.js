import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
}))

vi.mock('../services/api', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    patch: mocks.patch,
  },
}))

import CategoryTagManagementView from './CategoryTagManagementView.vue'

const categories = [
  {
    id: 1,
    code: 'movie',
    name: '电影',
    description: '影视长片',
    sortOrder: 10,
    enabled: true,
  },
  {
    id: 2,
    code: 'game',
    name: '游戏',
    description: '',
    sortOrder: 20,
    enabled: false,
  },
]

const tags = [
  {
    id: 10,
    code: 'movie_action',
    name: '动作',
    description: '动作片',
    categoryId: 1,
    categoryName: '电影',
    sortOrder: 5,
    enabled: true,
  },
]

function categoryItem(id) {
  return {
    id,
    code: `cat_${id}`,
    name: `Category ${id}`,
    description: '',
    sortOrder: id,
    enabled: true,
  }
}

function tagItem(id, categoryId) {
  return {
    id,
    code: `tag_${id}`,
    name: `Tag ${id}`,
    description: '',
    categoryId,
    categoryName: `Category ${categoryId}`,
    sortOrder: id,
    enabled: true,
  }
}

function mockInitialLoad(categoryData = categories, tagData = tags) {
  mocks.get.mockImplementation((url) => {
    if (url === '/api/admin/categories') return Promise.resolve({ data: categoryData })
    if (url === '/api/admin/tags') return Promise.resolve({ data: tagData })
    return Promise.reject(new Error(`Unexpected GET ${url}`))
  })
}

describe('admin category and tag management', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset())
    mockInitialLoad()
  })

  it('loads categories and tags for the admin directory', async () => {
    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    expect(mocks.get).toHaveBeenCalledWith('/api/admin/categories')
    expect(mocks.get).toHaveBeenCalledWith('/api/admin/tags', {
      params: { categoryId: 1 },
    })
    expect(wrapper.text()).toContain('movie')
    expect(wrapper.text()).toContain('电影')
    expect(wrapper.text()).toContain('movie_action')
    expect(wrapper.text()).toContain('动作')
    expect(wrapper.text()).toContain('已禁用')
  })

  it('creates a category with a trimmed payload and clears the form', async () => {
    mocks.post.mockResolvedValueOnce({
      data: {
        id: 3,
        code: 'music',
        name: '音乐',
        description: '现场与翻唱',
        sortOrder: 30,
        enabled: true,
      },
    })
    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    await wrapper.get('#category-code').setValue('  music  ')
    await wrapper.get('#category-name').setValue(' 音乐 ')
    await wrapper.get('#category-description').setValue(' 现场与翻唱 ')
    await wrapper.get('#category-sort').setValue('30')
    await wrapper.get('[data-test="category-form"]').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/api/admin/categories', {
      code: 'music',
      name: '音乐',
      description: '现场与翻唱',
      sortOrder: 30,
    })
    expect(wrapper.get('#category-code').element.value).toBe('')
    expect(wrapper.text()).toContain('music')
  })

  it('edits a category and toggles its enabled state', async () => {
    mocks.put.mockResolvedValueOnce({
      data: { ...categories[0], name: '电影精选', enabled: true },
    })
    mocks.patch.mockResolvedValueOnce({
      data: { ...categories[0], name: '电影精选', enabled: false },
    })
    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    await wrapper.get('[data-test="edit-category-1"]').trigger('click')
    expect(wrapper.get('#category-code').element.value).toBe('movie')
    await wrapper.get('#category-name').setValue('电影精选')
    await wrapper.get('[data-test="category-form"]').trigger('submit')
    await flushPromises()

    expect(mocks.put).toHaveBeenCalledWith('/api/admin/categories/1', {
      code: 'movie',
      name: '电影精选',
      description: '影视长片',
      sortOrder: 10,
    })

    await wrapper.get('[data-test="toggle-category-1"]').trigger('click')
    await flushPromises()

    expect(mocks.patch).toHaveBeenCalledWith('/api/admin/categories/1/enabled', { enabled: false })
    expect(wrapper.text()).toContain('已禁用')
  })

  it('creates and edits tags under a selected category', async () => {
    mocks.post.mockResolvedValueOnce({
      data: {
        id: 11,
        code: 'movie_comedy',
        name: '喜剧',
        description: '',
        categoryId: 1,
        categoryName: '电影',
        sortOrder: 6,
        enabled: true,
      },
    })
    mocks.put.mockResolvedValueOnce({
      data: { ...tags[0], name: '动作精选' },
    })
    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    await wrapper.get('#tag-category').setValue('1')
    await wrapper.get('#tag-code').setValue('movie_comedy')
    await wrapper.get('#tag-name').setValue('喜剧')
    await wrapper.get('#tag-sort').setValue('6')
    await wrapper.get('[data-test="tag-form"]').trigger('submit')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/api/admin/tags', {
      code: 'movie_comedy',
      name: '喜剧',
      description: '',
      categoryId: 1,
      sortOrder: 6,
    })

    await wrapper.get('[data-test="edit-tag-10"]').trigger('click')
    await wrapper.get('#tag-name').setValue('动作精选')
    await wrapper.get('[data-test="tag-form"]').trigger('submit')
    await flushPromises()

    expect(mocks.put).toHaveBeenCalledWith('/api/admin/tags/10', {
      code: 'movie_action',
      name: '动作精选',
      description: '动作片',
      categoryId: 1,
      sortOrder: 5,
    })
  })

  it('filters tags by category and toggles a tag as hidden', async () => {
    mocks.get.mockImplementation((url, options) => {
      if (url === '/api/admin/categories') return Promise.resolve({ data: categories })
      if (url === '/api/admin/tags') {
        return Promise.resolve({ data: options?.params?.categoryId === 1 ? tags : [] })
      }
      return Promise.reject(new Error(`Unexpected GET ${url}`))
    })
    mocks.patch.mockResolvedValueOnce({
      data: { ...tags[0], enabled: false },
    })
    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    await wrapper.get('[data-test="tag-filter"]').setValue('1')
    await flushPromises()

    expect(mocks.get).toHaveBeenLastCalledWith('/api/admin/tags', {
      params: { categoryId: 1 },
    })

    await wrapper.get('[data-test="toggle-tag-10"]').trigger('click')
    await flushPromises()

    expect(mocks.patch).toHaveBeenCalledWith('/api/admin/tags/10/enabled', { enabled: false })
  })

  it('selects a category and loads only its tag list', async () => {
    const gameTags = [tagItem(21, 2)]
    mocks.get.mockImplementation((url, options) => {
      if (url === '/api/admin/categories') return Promise.resolve({ data: categories })
      if (url === '/api/admin/tags') {
        return Promise.resolve({ data: options?.params?.categoryId === 2 ? gameTags : tags })
      }
      return Promise.reject(new Error(`Unexpected GET ${url}`))
    })
    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    expect(mocks.get).toHaveBeenLastCalledWith('/api/admin/tags', {
      params: { categoryId: 1 },
    })

    await wrapper.get('[data-test="select-category-2"]').trigger('click')
    await flushPromises()

    expect(mocks.get).toHaveBeenLastCalledWith('/api/admin/tags', {
      params: { categoryId: 2 },
    })
    expect(wrapper.text()).toContain('Tag 21')
    expect(wrapper.text()).not.toContain('movie_action')
    expect(wrapper.get('#tag-category').element.value).toBe('2')
  })

  it('paginates category and tag lists', async () => {
    const manyCategories = Array.from({ length: 12 }, (_, index) => categoryItem(index + 1))
    const manyTags = Array.from({ length: 12 }, (_, index) => tagItem(index + 1, 1))
    mockInitialLoad(manyCategories, manyTags)

    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    expect(wrapper.get('[data-test="category-page"]').text()).toContain('PAGE 1 / 2')
    expect(wrapper.get('[data-test="category-list-body"]').text()).toContain('cat_10')
    expect(wrapper.get('[data-test="category-list-body"]').text()).not.toContain('cat_11')
    await wrapper.get('[data-test="category-next"]').trigger('click')
    expect(wrapper.get('[data-test="category-page"]').text()).toContain('PAGE 2 / 2')
    expect(wrapper.get('[data-test="category-list-body"]').text()).toContain('cat_11')

    expect(wrapper.get('[data-test="tag-page"]').text()).toContain('PAGE 1 / 2')
    expect(wrapper.get('[data-test="tag-list-body"]').text()).toContain('tag_10')
    expect(wrapper.get('[data-test="tag-list-body"]').text()).not.toContain('tag_11')
    await wrapper.get('[data-test="tag-next"]').trigger('click')
    expect(wrapper.get('[data-test="tag-page"]').text()).toContain('PAGE 2 / 2')
    expect(wrapper.get('[data-test="tag-list-body"]').text()).toContain('tag_11')
  })

  it('validates forms and hides raw service errors', async () => {
    mocks.post.mockRejectedValueOnce({ response: { status: 409, data: 'duplicate key detail' } })
    const wrapper = mount(CategoryTagManagementView)
    await flushPromises()

    await wrapper.get('#category-code').setValue('x'.repeat(65))
    await wrapper.get('#category-name').setValue('电影')
    await wrapper.get('[data-test="category-form"]').trigger('submit')
    expect(wrapper.get('[data-test="category-message"]').text()).toContain('不能超过 64 个字符')
    expect(mocks.post).not.toHaveBeenCalled()

    await wrapper.get('#category-code').setValue('movie')
    await wrapper.get('[data-test="category-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-test="category-message"]').text()).toContain('保存分类失败')
    expect(wrapper.text()).not.toContain('duplicate key detail')

    await wrapper.get('#tag-code').setValue('movie_new')
    await wrapper.get('#tag-name').setValue('新标签')
    await wrapper.get('#tag-category').setValue('')
    await wrapper.get('[data-test="tag-form"]').trigger('submit')
    expect(wrapper.get('[data-test="tag-message"]').text()).toContain('请选择所属分类')
  })
})
