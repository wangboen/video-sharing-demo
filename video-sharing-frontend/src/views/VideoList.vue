<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import CategoryChips from '../components/CategoryChips.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import VideoCard from '../components/VideoCard.vue'
import api from '../services/api'
import { matchesCategory } from '../utils/video'

const PAGE_SIZE = 12
const route = useRoute()
const videos = ref([])
const loading = ref(true)
const error = ref('')
const selectedCategory = ref('全部')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

const searchQuery = computed(() => String(route.query.q ?? '').trim())
const filteredVideos = computed(() => videos.value.filter((video) => matchesCategory(video, selectedCategory.value)))

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
    const response = searchQuery.value
      ? await api.get('/api/videos/search', {
          params: { q: searchQuery.value, page, size: PAGE_SIZE },
        })
      : await api.get('/api/videos')
    applyPage(response.data, page)
  } catch {
    error.value = '视频加载失败，请检查服务是否正常运行。'
  } finally {
    loading.value = false
  }
}

function loadPage(page) {
  if (loading.value || page < 0 || (totalPages.value && page >= totalPages.value)) {
    return
  }
  loadVideos(page)
}

onMounted(() => loadVideos(0))
watch(searchQuery, () => {
  selectedCategory.value = '全部'
  loadVideos(0)
})
</script>

<template>
  <section class="video-list">
    <header class="video-list__header">
      <div>
        <p class="video-list__eyebrow">VISION / LIBRARY</p>
        <h1>全部视频</h1>
        <p v-if="searchQuery" class="video-list__description">“{{ searchQuery }}” 的搜索结果</p>
      </div>
      <CategoryChips v-model="selectedCategory" />
    </header>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" :message="error" @retry="loadVideos" />
    <div v-else-if="filteredVideos.length" class="video-list__grid">
      <VideoCard v-for="video in filteredVideos" :key="video.id" :video="video" />
    </div>
    <EmptyState v-else title="没有找到视频" :description="searchQuery ? '试试其他关键词，或切换分类。' : videos.length ? '该分类暂时没有视频，换一个分类看看。' : '视频发布后会显示在这里。'" action-to="/" action-label="查看推荐" />

    <nav v-if="!loading && totalPages > 1" class="video-list__pagination" aria-label="视频分页">
      <button
        class="video-list__page-prev"
        type="button"
        :disabled="currentPage <= 0"
        @click="loadPage(currentPage - 1)"
      >
        上一页
      </button>
      <span>{{ currentPage + 1 }} / {{ totalPages }} · {{ totalElements }}</span>
      <button
        class="video-list__page-next"
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
.video-list { margin: 0 auto; max-width: 1440px; }
.video-list__header { align-items: flex-end; display: flex; gap: 24px; justify-content: space-between; margin-bottom: 30px; }
.video-list__eyebrow { color: var(--brand); font-size: .72rem; font-weight: 800; letter-spacing: .15em; margin: 0 0 6px; }
h1 { font-size: clamp(1.8rem, 3vw, 2.7rem); letter-spacing: -.05em; margin: 0; } .video-list__description { color: var(--muted); margin: 8px 0 0; }
.video-list__grid { display: grid; gap: 18px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
.video-list__pagination { align-items: center; display: flex; gap: 12px; justify-content: center; margin-top: 28px; }
.video-list__pagination button { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; color: var(--text); cursor: pointer; font: inherit; font-weight: 750; padding: 11px 14px; }
.video-list__pagination button:disabled { cursor: not-allowed; opacity: .55; }
.video-list__pagination span { color: var(--muted); font-weight: 700; }
@media (max-width: 760px) { .video-list__header { align-items: flex-start; flex-direction: column; } }
</style>
