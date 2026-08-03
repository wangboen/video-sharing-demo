<script setup>
import { computed, onMounted, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import VideoCard from '../components/VideoCard.vue'
import api from '../services/api'
import { friendlyApiError } from '../utils/apiError'

const PAGE_SIZE = 12

const videos = ref([])
const loading = ref(true)
const error = ref('')
const categories = ref([])
const selectedCategoryId = ref(null)
const categoryLoading = ref(false)
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

const categoryOptions = computed(() => [{ id: null, name: '全部' }, ...categories.value])
const emptyState = computed(() => {
  return videos.value.length === 0 && selectedCategoryId.value == null
    ? {
        title: '还没有视频',
        description: '发布第一段作品，开启视界里的创作旅程。',
        actionTo: '/upload',
        actionLabel: '发布视频',
      }
    : {
        title: '还没有匹配的视频',
        description: '换一个分类看看，或稍后再回来探索。',
        actionTo: '/videos',
        actionLabel: '浏览全部视频',
      }
})

function applyPage(data, page) {
  if (Array.isArray(data)) {
    videos.value = data
    currentPage.value = 0
    totalPages.value = data.length ? 1 : 0
    totalElements.value = data.length
    return
  }
  videos.value = Array.isArray(data?.content) ? data.content : []
  currentPage.value = Number.isInteger(data?.number) ? data.number : page
  totalPages.value = Number.isInteger(data?.totalPages) ? data.totalPages : 0
  totalElements.value = Number.isInteger(data?.totalElements) ? data.totalElements : videos.value.length
}

async function loadVideos(page = 0) {
  loading.value = true
  error.value = ''
  try {
    const params = { page, size: PAGE_SIZE }
    if (selectedCategoryId.value) params.categoryId = selectedCategoryId.value
    const response = await api.get('/api/videos/recommended', {
      params,
    })
    applyPage(response.data, page)
  } catch (err) {
    error.value = friendlyApiError(err, '视频加载失败，请检查服务是否正常运行。')
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categoryLoading.value = true
  try {
    const categoriesResponse = await api.get('/api/categories')
    categories.value = Array.isArray(categoriesResponse.data) ? categoriesResponse.data : []
  } catch {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

function selectCategory(categoryId) {
  selectedCategoryId.value = categoryId
  loadVideos(0)
}

function loadPage(page) {
  if (loading.value || page < 0 || (totalPages.value && page >= totalPages.value)) {
    return
  }
  loadVideos(page)
}

onMounted(() => {
  loadVideos(0)
  loadCategories()
})
</script>

<template>
  <section class="video-feed">
    <header class="video-feed__header">
      <div>
        <p class="video-feed__eyebrow">VISION / RECOMMENDED</p>
        <h1>为你推荐</h1>
        <p class="video-feed__description">
          按推荐缓存挑选最近值得停留的视频。
        </p>
      </div>
      <nav class="recommendation-categories" aria-label="推荐分类">
        <button
          v-for="category in categoryOptions"
          :key="category.id ?? 'all'"
          class="recommendation-categories__item"
          :class="{ 'recommendation-categories__item--active': selectedCategoryId === category.id }"
          type="button"
          :aria-pressed="selectedCategoryId === category.id"
          :data-testid="category.id == null ? 'recommendation-category-all' : `recommendation-category-${category.id}`"
          @click="selectCategory(category.id)"
        >
          {{ category.name }}
        </button>
        <span v-if="categoryLoading" class="recommendation-categories__loading">分类加载中...</span>
      </nav>
    </header>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" :message="error" @retry="loadVideos(currentPage)" />
    <div v-else-if="videos.length" class="video-feed__grid">
      <VideoCard v-for="video in videos" :key="video.id" :video="video" />
    </div>
    <EmptyState
      v-else
      :title="emptyState.title"
      :description="emptyState.description"
      :action-to="emptyState.actionTo"
      :action-label="emptyState.actionLabel"
    />

    <nav v-if="!loading && totalPages > 1" class="video-feed__pagination" aria-label="视频分页">
      <button
        class="video-feed__page-prev"
        type="button"
        :disabled="currentPage <= 0"
        @click="loadPage(currentPage - 1)"
      >
        上一页
      </button>
      <span>{{ currentPage + 1 }} / {{ totalPages }} · {{ totalElements }}</span>
      <button
        class="video-feed__page-next"
        type="button"
        :disabled="currentPage >= totalPages - 1"
        @click="loadPage(currentPage + 1)"
      >
        下一页
      </button>
    </nav>
  </section>
</template>

<style scoped>
.video-feed { margin: 0 auto; max-width: 1440px; }
.video-feed__header { align-items: flex-end; display: flex; gap: 24px; justify-content: space-between; margin-bottom: 22px; }
.video-feed__eyebrow { color: var(--brand); font-size: .72rem; font-weight: 800; letter-spacing: .15em; margin: 0 0 6px; }
h1 { font-size: clamp(1.8rem, 3vw, 2.7rem); letter-spacing: -.05em; margin: 0; }
.video-feed__description { color: var(--muted); margin: 8px 0 0; }
.recommendation-categories { align-items: center; display: flex; flex-wrap: wrap; gap: 10px; justify-content: flex-end; max-width: 620px; }
.recommendation-categories__item { background: var(--surface); border: 1px solid var(--border); border-radius: 999px; color: var(--muted); cursor: pointer; font: inherit; padding: 7px 14px; transition: background 160ms ease, border-color 160ms ease, color 160ms ease; }
.recommendation-categories__item:hover { border-color: color-mix(in srgb, var(--brand) 50%, var(--border)); color: var(--text); }
.recommendation-categories__item--active { background: var(--brand-action); border-color: var(--brand-action); color: var(--on-brand); font-weight: 700; }
.recommendation-categories__loading { color: var(--muted); font-size: .82rem; font-weight: 700; }
.video-feed__pagination button { border: 1px solid var(--border); border-radius: 10px; cursor: pointer; font: inherit; font-weight: 750; padding: 11px 14px; }
.video-feed__pagination button { background: var(--surface); color: var(--text); }
.video-feed__grid { display: grid; gap: 18px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
.video-feed__pagination { align-items: center; display: flex; gap: 12px; justify-content: center; margin-top: 28px; }
.video-feed__pagination button:disabled { cursor: not-allowed; opacity: .55; }
.video-feed__pagination span { color: var(--muted); font-weight: 700; }
@media (max-width: 760px) {
  .video-feed__header { align-items: flex-start; flex-direction: column; }
}
</style>
