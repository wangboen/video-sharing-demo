<script setup>
import { computed, onMounted, ref } from 'vue'
import CategoryChips from '../components/CategoryChips.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import VideoCard from '../components/VideoCard.vue'
import api from '../services/api'
import { matchesCategory } from '../utils/video'

const videos = ref([])
const loading = ref(true)
const error = ref('')
const selectedCategory = ref('全部')

const filteredVideos = computed(() => videos.value.filter((video) => matchesCategory(video, selectedCategory.value)))
const emptyState = computed(() => videos.value.length === 0
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
    })

async function loadVideos() {
  loading.value = true
  error.value = ''
  try {

    // 在 main.ts 或 App.vue 中
    console.log('当前 API Base URL:', import.meta.env.VITE_API_BASE_URL);
    console.log('所有环境变量:', import.meta.env); // 查看全部

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
  <section class="video-feed">
    <header class="video-feed__header">
      <div>
        <p class="video-feed__eyebrow">VISION / DISCOVER</p>
        <h1>为你推荐</h1>
        <p class="video-feed__description">从每一帧灵感里，发现值得停留的视界。</p>
      </div>
      <CategoryChips v-model="selectedCategory" />
    </header>

    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" :message="error" @retry="loadVideos" />
    <div v-else-if="filteredVideos.length" class="video-feed__grid">
      <VideoCard v-for="video in filteredVideos" :key="video.id" :video="video" />
    </div>
    <EmptyState
      v-else
      :title="emptyState.title"
      :description="emptyState.description"
      :action-to="emptyState.actionTo"
      :action-label="emptyState.actionLabel"
    />
  </section>
</template>

<style scoped>
.video-feed { margin: 0 auto; max-width: 1440px; }
.video-feed__header { align-items: flex-end; display: flex; gap: 24px; justify-content: space-between; margin-bottom: 30px; }
.video-feed__eyebrow { color: var(--brand); font-size: .72rem; font-weight: 800; letter-spacing: .15em; margin: 0 0 6px; }
h1 { font-size: clamp(1.8rem, 3vw, 2.7rem); letter-spacing: -.05em; margin: 0; } .video-feed__description { color: var(--muted); margin: 8px 0 0; }
.video-feed__grid { display: grid; gap: 18px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
@media (max-width: 760px) { .video-feed__header { align-items: flex-start; flex-direction: column; } }
</style>
