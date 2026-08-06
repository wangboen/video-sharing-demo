<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '../services/api'

const PAGE_SIZE = 10

const EMPTY_CATEGORY = {
  id: null,
  code: '',
  name: '',
  description: '',
  sortOrder: 0,
}

const EMPTY_TAG = {
  id: null,
  code: '',
  name: '',
  description: '',
  categoryId: '',
  sortOrder: 0,
}

const categories = ref([])
const tags = ref([])
const loading = ref(true)
const loadingTags = ref(true)
const categoryFilter = ref('')
const categoryMessage = ref('')
const categoryMessageKind = ref('status')
const tagMessage = ref('')
const tagMessageKind = ref('status')
const listError = ref('')
const tagListError = ref('')
const savingCategory = ref(false)
const savingTag = ref(false)
const categoryPage = ref(0)
const tagPage = ref(0)

const categoryForm = reactive({ ...EMPTY_CATEGORY })
const tagForm = reactive({ ...EMPTY_TAG })

const categoryMode = computed(() => (categoryForm.id ? 'edit' : 'create'))
const tagMode = computed(() => (tagForm.id ? 'edit' : 'create'))
const categoryTotalPages = computed(() => Math.max(Math.ceil(categories.value.length / PAGE_SIZE), 1))
const tagTotalPages = computed(() => Math.max(Math.ceil(tags.value.length / PAGE_SIZE), 1))
const pagedCategories = computed(() => pageSlice(categories.value, categoryPage.value))
const pagedTags = computed(() => pageSlice(tags.value, tagPage.value))
const hasCategoryPrevious = computed(() => categoryPage.value > 0)
const hasCategoryNext = computed(() => categoryPage.value + 1 < categoryTotalPages.value)
const hasTagPrevious = computed(() => tagPage.value > 0)
const hasTagNext = computed(() => tagPage.value + 1 < tagTotalPages.value)
const categoryById = computed(() => {
  const map = new Map()
  categories.value.forEach((category) => map.set(category.id, category))
  return map
})

function pageSlice(items, page) {
  const start = page * PAGE_SIZE
  return items.slice(start, start + PAGE_SIZE)
}

function normalizeSort(value) {
  const parsed = Number.parseInt(String(value ?? '0'), 10)
  return Number.isFinite(parsed) ? parsed : 0
}

function resetCategoryForm() {
  Object.assign(categoryForm, EMPTY_CATEGORY)
}

function resetTagForm() {
  Object.assign(tagForm, EMPTY_TAG)
}

function upsertCategory(category) {
  categories.value = [
    category,
    ...categories.value.filter((item) => item.id !== category.id),
  ].sort(sortTaxonomy)
}

function upsertTag(tag) {
  const selectedCategoryId = categoryFilter.value ? Number(categoryFilter.value) : null
  if (selectedCategoryId && tag.categoryId !== selectedCategoryId) {
    tags.value = tags.value.filter((item) => item.id !== tag.id)
    return
  }
  tags.value = [
    tag,
    ...tags.value.filter((item) => item.id !== tag.id),
  ].sort(sortTaxonomy)
}

function sortTaxonomy(left, right) {
  const leftSort = normalizeSort(left.sortOrder)
  const rightSort = normalizeSort(right.sortOrder)
  if (leftSort !== rightSort) return leftSort - rightSort
  return Number(left.id ?? 0) - Number(right.id ?? 0)
}

function validateBase(form, label) {
  const code = form.code.trim()
  const name = form.name.trim()
  const description = form.description.trim()
  if (!code) return `${label}编码不能为空`
  if (!name) return `${label}名称不能为空`
  if (code.length > 64 || name.length > 64) return `${label}编码和名称不能超过 64 个字符`
  if (description.length > 255) return `${label}描述不能超过 255 个字符`
  return ''
}

function categoryPayload() {
  return {
    code: categoryForm.code.trim(),
    name: categoryForm.name.trim(),
    description: categoryForm.description.trim(),
    sortOrder: normalizeSort(categoryForm.sortOrder),
  }
}

function tagPayload() {
  return {
    code: tagForm.code.trim(),
    name: tagForm.name.trim(),
    description: tagForm.description.trim(),
    categoryId: Number(tagForm.categoryId),
    sortOrder: normalizeSort(tagForm.sortOrder),
  }
}

function enabledStatusText(item) {
  return item.enabled ? '已启用' : '已禁用'
}

function toggleEnabledLabel(item) {
  return item.enabled ? '禁用' : '恢复'
}

function nextEnabledValue(item) {
  return !item.enabled
}

async function loadCategories() {
  loading.value = true
  listError.value = ''
  try {
    const { data } = await api.get('/api/admin/categories')

    console.log(data)

    categories.value = Array.isArray(data) ? data.slice().sort(sortTaxonomy) : []
    categoryPage.value = Math.min(categoryPage.value, categoryTotalPages.value - 1)
    if (!categoryFilter.value && categories.value.length) {
      categoryFilter.value = String(categories.value[0].id)
      tagForm.categoryId = categoryFilter.value
    }
  } catch {
    listError.value = '分类列表暂时无法加载，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function loadTags() {
  loadingTags.value = true
  tagListError.value = ''
  tagPage.value = 0
  try {
    const categoryId = categoryFilter.value ? Number(categoryFilter.value) : null
    const { data } = categoryId
      ? await api.get('/api/admin/tags', { params: { categoryId } })
      : await api.get('/api/admin/tags')
    tags.value = Array.isArray(data) ? data.slice().sort(sortTaxonomy) : []
  } catch {
    tagListError.value = '标签列表暂时无法加载，请稍后重试。'
  } finally {
    loadingTags.value = false
  }
}

async function selectCategory(category) {
  categoryFilter.value = String(category.id)
  tagForm.categoryId = categoryFilter.value
  resetTagForm()
  tagForm.categoryId = categoryFilter.value
  await loadTags()
}

async function saveCategory() {
  if (savingCategory.value) return
  categoryMessage.value = validateBase(categoryForm, '分类')
  categoryMessageKind.value = categoryMessage.value ? 'error' : 'status'
  if (categoryMessage.value) return

  savingCategory.value = true
  try {
    const endpoint = categoryForm.id
      ? `/api/admin/categories/${categoryForm.id}`
      : '/api/admin/categories'
    const request = categoryForm.id ? api.put : api.post
    const { data } = await request(endpoint, categoryPayload())
    upsertCategory(data)
    categoryMessage.value = categoryForm.id ? '分类已更新。' : '分类已创建。'
    categoryMessageKind.value = 'status'
    resetCategoryForm()
  } catch {
    categoryMessage.value = '保存分类失败，请检查编码是否重复后重试。'
    categoryMessageKind.value = 'error'
  } finally {
    savingCategory.value = false
  }
}

async function saveTag() {
  if (savingTag.value) return
  tagMessage.value = validateBase(tagForm, '标签')
  if (!tagMessage.value && !tagForm.categoryId) {
    tagMessage.value = '请选择所属分类'
  }
  tagMessageKind.value = tagMessage.value ? 'error' : 'status'
  if (tagMessage.value) return

  savingTag.value = true
  try {
    const endpoint = tagForm.id ? `/api/admin/tags/${tagForm.id}` : '/api/admin/tags'
    const request = tagForm.id ? api.put : api.post
    const { data } = await request(endpoint, tagPayload())
    upsertTag(data)
    tagMessage.value = tagForm.id ? '标签已更新。' : '标签已创建。'
    tagMessageKind.value = 'status'
    resetTagForm()
  } catch {
    tagMessage.value = '保存标签失败，请检查编码是否重复后重试。'
    tagMessageKind.value = 'error'
  } finally {
    savingTag.value = false
  }
}

function editCategory(category) {
  Object.assign(categoryForm, {
    id: category.id,
    code: category.code ?? '',
    name: category.name ?? '',
    description: category.description ?? '',
    sortOrder: normalizeSort(category.sortOrder),
  })
  categoryMessage.value = ''
}

function editTag(tag) {
  Object.assign(tagForm, {
    id: tag.id,
    code: tag.code ?? '',
    name: tag.name ?? '',
    description: tag.description ?? '',
    categoryId: tag.categoryId ? String(tag.categoryId) : '',
    sortOrder: normalizeSort(tag.sortOrder),
  })
  tagMessage.value = ''
}

async function toggleCategory(category) {
  try {
    const { data } = await api.patch(`/api/admin/categories/${category.id}/enabled`, {
      enabled: nextEnabledValue(category),
    })
    upsertCategory(data)
  } catch {
    categoryMessage.value = '分类状态更新失败，请稍后重试。'
    categoryMessageKind.value = 'error'
  }
}

async function toggleTag(tag) {
  try {
    const { data } = await api.patch(`/api/admin/tags/${tag.id}/enabled`, {
      enabled: nextEnabledValue(tag),
    })
    upsertTag(data)
  } catch {
    tagMessage.value = '标签状态更新失败，请稍后重试。'
    tagMessageKind.value = 'error'
  }
}

function categoryName(tag) {
  return tag.categoryName || categoryById.value.get(tag.categoryId)?.name || '-'
}

async function refreshAll() {
  await loadCategories()
  await loadTags()
}

onMounted(async () => {
  await refreshAll()
})
</script>

<template>
  <main class="page taxonomy-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">内容目录 / ADMIN</p>
        <h1>分类与标签管理</h1>
        <p>维护投稿分类和标签库，禁用后前台隐藏，后台仍可恢复。</p>
      </div>
      <button class="button button--quiet" type="button" :disabled="loading || loadingTags" @click="refreshAll">
        刷新目录
      </button>
    </header>

    <div class="taxonomy-layout">
      <section class="paper-panel taxonomy-editor" aria-labelledby="category-form-title">
        <p class="eyebrow">{{ categoryMode === 'edit' ? '编辑分类' : '新增分类' }}</p>
        <h2 id="category-form-title">{{ categoryMode === 'edit' ? '更新分类' : '创建分类' }}</h2>
        <form class="form-stack" data-test="category-form" novalidate @submit.prevent="saveCategory">
          <label for="category-code">分类编码</label>
          <input id="category-code" v-model="categoryForm.code" maxlength="64" autocomplete="off" required>

          <label for="category-name">分类名称</label>
          <input id="category-name" v-model="categoryForm.name" maxlength="64" autocomplete="off" required>

          <label for="category-description">描述</label>
          <textarea id="category-description" v-model="categoryForm.description" maxlength="255" rows="3" />

          <label for="category-sort">排序</label>
          <input id="category-sort" v-model="categoryForm.sortOrder" type="number" step="1">

          <p
            v-if="categoryMessage"
            class="message"
            :class="{ 'message--error': categoryMessageKind === 'error' }"
            :role="categoryMessageKind === 'error' ? 'alert' : 'status'"
            data-test="category-message"
          >
            {{ categoryMessage }}
          </p>
          <div class="form-actions">
            <button class="button button--primary" type="submit" :disabled="savingCategory">
              {{ savingCategory ? '保存中...' : (categoryMode === 'edit' ? '保存分类' : '创建分类') }}
            </button>
            <button v-if="categoryMode === 'edit'" class="button button--quiet" type="button" @click="resetCategoryForm">
              取消编辑
            </button>
          </div>
        </form>
      </section>

      <section class="paper-panel taxonomy-directory" aria-labelledby="category-list-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">分类目录</p>
            <h2 id="category-list-title">Category</h2>
          </div>
          <span class="count-chip telemetry">{{ categories.length }} CATEGORIES</span>
        </div>

        <p v-if="loading" class="inline-state" role="status">正在加载分类...</p>
        <p v-else-if="listError" class="message message--error" role="alert">{{ listError }}</p>
        <div v-else-if="!categories.length" class="directory-empty">
          <h3>暂无分类</h3>
          <p>使用左侧表单创建第一个投稿分类。</p>
        </div>
        <div v-else class="reviewer-table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">分类</th>
                <th scope="col">描述</th>
                <th scope="col">排序</th>
                <th scope="col">状态</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody data-test="category-list-body">
              <tr
                v-for="category in pagedCategories"
                :key="category.id"
                :class="{
                  'is-muted-row': !category.enabled,
                  'is-selected': categoryFilter === String(category.id),
                }"
              >
                <td>
                  <strong>{{ category.name }}</strong>
                  <small class="telemetry">{{ category.code }}</small>
                </td>
                <td>{{ category.description || '-' }}</td>
                <td class="telemetry">{{ category.sortOrder ?? 0 }}</td>
                <td>
                  <span class="role-chip telemetry" :class="{ 'role-chip--disabled': !category.enabled }">
                    {{ enabledStatusText(category) }}
                  </span>
                </td>
                <td>
                  <div class="table-actions">
                    <button class="button button--quiet button--compact" type="button" :data-test="`select-category-${category.id}`" @click="selectCategory(category)">
                      查看标签
                    </button>
                    <button class="button button--quiet button--compact" type="button" :data-test="`edit-category-${category.id}`" @click="editCategory(category)">
                      编辑
                    </button>
                    <button class="button button--quiet button--compact" type="button" :data-test="`toggle-category-${category.id}`" @click="toggleCategory(category)">
                      {{ toggleEnabledLabel(category) }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="categories.length" class="pagination-bar">
          <span class="telemetry" data-test="category-page">PAGE {{ categoryPage + 1 }} / {{ categoryTotalPages }}</span>
          <div>
            <button class="button button--quiet button--compact" type="button" data-test="category-previous" :disabled="!hasCategoryPrevious" @click="categoryPage -= 1">
              上一页
            </button>
            <button class="button button--quiet button--compact" type="button" data-test="category-next" :disabled="!hasCategoryNext" @click="categoryPage += 1">
              下一页
            </button>
          </div>
        </div>
      </section>
    </div>

    <div class="taxonomy-layout taxonomy-layout--tags">
      <section class="paper-panel taxonomy-editor" aria-labelledby="tag-form-title">
        <p class="eyebrow">{{ tagMode === 'edit' ? '编辑标签' : '新增标签' }}</p>
        <h2 id="tag-form-title">{{ tagMode === 'edit' ? '更新标签' : '创建标签' }}</h2>
        <form class="form-stack" data-test="tag-form" novalidate @submit.prevent="saveTag">
          <label for="tag-category">所属分类</label>
          <select id="tag-category" v-model="tagForm.categoryId" required>
            <option value="">请选择分类</option>
            <option v-for="category in categories" :key="category.id" :value="String(category.id)">
              {{ category.name }} / {{ category.code }}
            </option>
          </select>

          <label for="tag-code">标签编码</label>
          <input id="tag-code" v-model="tagForm.code" maxlength="64" autocomplete="off" required>

          <label for="tag-name">标签名称</label>
          <input id="tag-name" v-model="tagForm.name" maxlength="64" autocomplete="off" required>

          <label for="tag-description">描述</label>
          <textarea id="tag-description" v-model="tagForm.description" maxlength="255" rows="3" />

          <label for="tag-sort">排序</label>
          <input id="tag-sort" v-model="tagForm.sortOrder" type="number" step="1">

          <p
            v-if="tagMessage"
            class="message"
            :class="{ 'message--error': tagMessageKind === 'error' }"
            :role="tagMessageKind === 'error' ? 'alert' : 'status'"
            data-test="tag-message"
          >
            {{ tagMessage }}
          </p>
          <div class="form-actions">
            <button class="button button--primary" type="submit" :disabled="savingTag">
              {{ savingTag ? '保存中...' : (tagMode === 'edit' ? '保存标签' : '创建标签') }}
            </button>
            <button v-if="tagMode === 'edit'" class="button button--quiet" type="button" @click="resetTagForm">
              取消编辑
            </button>
          </div>
        </form>
      </section>

      <section class="paper-panel taxonomy-directory" aria-labelledby="tag-list-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">标签目录</p>
            <h2 id="tag-list-title">Tag</h2>
          </div>
          <span class="count-chip telemetry">{{ tags.length }} TAGS</span>
        </div>

        <div class="toolbar-form">
          <select v-model="categoryFilter" data-test="tag-filter" @change="loadTags">
            <option v-for="category in categories" :key="category.id" :value="String(category.id)">
              {{ category.name }}
            </option>
          </select>
          <button class="button button--quiet" type="button" :disabled="loadingTags" @click="loadTags">刷新标签</button>
        </div>

        <p v-if="loadingTags" class="inline-state" role="status">正在加载标签...</p>
        <p v-else-if="tagListError" class="message message--error" role="alert">{{ tagListError }}</p>
        <div v-else-if="!tags.length" class="directory-empty">
          <h3>暂无标签</h3>
          <p>选择分类后创建可用于投稿的标签。</p>
        </div>
        <div v-else class="reviewer-table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">标签</th>
                <th scope="col">所属分类</th>
                <th scope="col">描述</th>
                <th scope="col">排序</th>
                <th scope="col">状态</th>
                <th scope="col">操作</th>
              </tr>
            </thead>
            <tbody data-test="tag-list-body">
              <tr v-for="tag in pagedTags" :key="tag.id" :class="{ 'is-muted-row': !tag.enabled }">
                <td>
                  <strong>{{ tag.name }}</strong>
                  <small class="telemetry">{{ tag.code }}</small>
                </td>
                <td>{{ categoryName(tag) }}</td>
                <td>{{ tag.description || '-' }}</td>
                <td class="telemetry">{{ tag.sortOrder ?? 0 }}</td>
                <td>
                  <span class="role-chip telemetry" :class="{ 'role-chip--disabled': !tag.enabled }">
                    {{ enabledStatusText(tag) }}
                  </span>
                </td>
                <td>
                  <div class="table-actions">
                    <button class="button button--quiet button--compact" type="button" :data-test="`edit-tag-${tag.id}`" @click="editTag(tag)">
                      编辑
                    </button>
                    <button class="button button--quiet button--compact" type="button" :data-test="`toggle-tag-${tag.id}`" @click="toggleTag(tag)">
                      {{ toggleEnabledLabel(tag) }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="tags.length" class="pagination-bar">
          <span class="telemetry" data-test="tag-page">PAGE {{ tagPage + 1 }} / {{ tagTotalPages }}</span>
          <div>
            <button class="button button--quiet button--compact" type="button" data-test="tag-previous" :disabled="!hasTagPrevious" @click="tagPage -= 1">
              上一页
            </button>
            <button class="button button--quiet button--compact" type="button" data-test="tag-next" :disabled="!hasTagNext" @click="tagPage += 1">
              下一页
            </button>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>
