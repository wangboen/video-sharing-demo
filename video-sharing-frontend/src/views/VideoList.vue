<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CategoryChips from '../components/CategoryChips.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import VideoCard from '../components/VideoCard.vue'
import api from '../services/api'
import { matchesCategory, matchesSearch } from '../utils/video'

const route = useRoute()
const videos = ref([])
const loading = ref(true)
const error = ref('')
const selectedCategory = ref('全部')

const searchQuery = computed(() => String(route.query.q ?? ''))
const filteredVideos = computed(() => videos.value.filter((video) => (
  matchesSearch(video, searchQuery.value) && matchesCategory(video, selectedCategory.value)
)))

async function loadVideos() {
  loading.value = true
  error.value = ''
  try {
    const response = await api.get('/api/videos')
    videos.value = Array.isArray(response.data) ? response.data : []
  } catch {
    error.value = '视频加载失败，请检查服务是否正常运行。'
  } finally {
    loading.value = false
  }
}

onMounted(loadVideos)
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
  </section>
</template>

<style scoped>
.video-list { margin: 0 auto; max-width: 1440px; }
.video-list__header { align-items: flex-end; display: flex; gap: 24px; justify-content: space-between; margin-bottom: 30px; }
.video-list__eyebrow { color: var(--brand); font-size: .72rem; font-weight: 800; letter-spacing: .15em; margin: 0 0 6px; }
h1 { font-size: clamp(1.8rem, 3vw, 2.7rem); letter-spacing: -.05em; margin: 0; } .video-list__description { color: var(--muted); margin: 8px 0 0; }
.video-list__grid { display: grid; gap: 18px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
@media (max-width: 760px) { .video-list__header { align-items: flex-start; flex-direction: column; } }
</style>
