<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import VideoListItem from '../components/VideoListItem.vue'
import api from '../services/api'
import { formatDate, formatViews, getThumbnailUrl, getVideoUrl } from '../utils/video'

const route = useRoute()
const video = ref(null)
const recommendations = ref([])
const loading = ref(true)
const recommendationsLoading = ref(true)
const error = ref('')
const recommendationsError = ref('')
const liked = ref(false)
const saved = ref(false)
let latestRequestVersion = 0

const authorName = computed(() => video.value?.user?.username || '匿名创作者')
const authorInitial = computed(() => authorName.value.trim().charAt(0).toUpperCase() || '视')
const publishDate = computed(() => video.value?.createdAt ?? video.value?.uploadDate ?? video.value?.date)
const authorBio = computed(() => video.value?.user?.bio || video.value?.user?.description || '这个创作者暂未留下简介。')

async function loadRecommendations(requestVersion, videoId) {
  const isLatestRequest = () => requestVersion === latestRequestVersion
  if (isLatestRequest()) {
    recommendationsLoading.value = true
    recommendationsError.value = ''
  }

  try {
    const listResponse = await api.get('/api/videos')
    if (!isLatestRequest()) return
    const list = Array.isArray(listResponse.data) ? listResponse.data : []
    recommendations.value = list
      .filter((item) => String(item.id) !== String(videoId))
      .slice(0, 12)
  } catch {
    if (isLatestRequest()) recommendationsError.value = '推荐视频加载失败，请稍后重试。'
  } finally {
    if (isLatestRequest()) recommendationsLoading.value = false
  }
}

function retryRecommendations() {
  return loadRecommendations(latestRequestVersion, route.params.id)
}

async function loadPage() {
  const requestVersion = ++latestRequestVersion
  const isLatestRequest = () => requestVersion === latestRequestVersion
  const videoId = route.params.id
  if (isLatestRequest()) {
    video.value = null
    recommendations.value = []
    loading.value = true
    recommendationsLoading.value = true
    error.value = ''
    recommendationsError.value = ''
    liked.value = false
    saved.value = false
  }

  const currentVideoRequest = api.get(`/api/videos/${videoId}`)
    .then((response) => {
      if (isLatestRequest()) video.value = response.data
    })
    .catch(() => {
      if (isLatestRequest()) error.value = '视频加载失败，请稍后重试。'
    })
    .finally(() => {
      if (isLatestRequest()) loading.value = false
    })

  const recommendationsRequest = loadRecommendations(requestVersion, videoId)

  await Promise.all([currentVideoRequest, recommendationsRequest])
}

watch(() => route.params.id, loadPage, { immediate: true })
</script>

<template>
  <section class="video-player-page">
    <LoadingState v-if="loading" :count="3" />
    <ErrorState v-else-if="error" :message="error" @retry="loadPage" />

    <div v-else-if="video" class="watch-layout">
      <section class="watch-layout__main">
        <video
          class="watch-player"
          controls
          :src="getVideoUrl(video)"
          :poster="getThumbnailUrl(video)"
        />

        <section class="video-details">
          <div class="video-details__heading">
            <div>
              <h1>{{ video.title || '未命名视频' }}</h1>
              <p class="video-details__meta">{{ formatViews(video.views) }} <span aria-hidden="true">·</span> {{ formatDate(publishDate) }}</p>
            </div>
            <div class="video-actions" aria-label="视频操作">
              <button type="button" :aria-pressed="liked" :class="{ 'is-active': liked }" @click="liked = !liked">点赞</button>
              <button type="button" :aria-pressed="saved" :class="{ 'is-active': saved }" @click="saved = !saved">稍后观看</button>
            </div>
          </div>
          <p class="local-action-note">点赞和稍后观看仅在当前页面和本次浏览中有效，不会同步到账号。</p>

          <div class="author-panel">
            <span class="author-panel__avatar" aria-hidden="true">{{ authorInitial }}</span>
            <div>
              <p class="author-panel__name">{{ authorName }}</p>
              <p class="author-panel__bio">{{ authorBio }}</p>
            </div>
          </div>

          <p v-if="video.description" class="video-description">{{ video.description }}</p>
        </section>
      </section>

      <aside class="watch-layout__aside" aria-label="接着看">
        <h2>接着看</h2>
        <div v-if="recommendationsError" class="recommendations-error" role="alert">
          <p>{{ recommendationsError }}</p>
          <button class="recommendations-retry" type="button" @click="retryRecommendations">重新加载推荐</button>
        </div>
        <LoadingState v-else-if="recommendationsLoading" :count="3" />
        <div v-else-if="recommendations.length" class="recommendations-list">
          <VideoListItem v-for="item in recommendations" :key="item.id" :video="item" />
        </div>
        <p v-else class="recommendations-empty">暂时没有更多推荐视频。</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.video-player-page { margin: 0 auto; max-width: 1440px; }
.watch-layout { display: grid; gap: 32px; grid-template-columns: minmax(0, 1fr) minmax(285px, 360px); }
.watch-player { aspect-ratio: 16 / 9; background: #080808; border-radius: var(--radius-md); display: block; max-height: 70vh; width: 100%; }
.video-details { padding: 22px 2px 0; }
.video-details__heading { align-items: flex-start; display: flex; gap: 20px; justify-content: space-between; }
h1 { font-size: clamp(1.45rem, 2.5vw, 2.15rem); letter-spacing: -.045em; line-height: 1.2; margin: 0; }
.video-details__meta { color: var(--muted); font-size: .85rem; margin: 10px 0 0; }
.video-actions { display: flex; flex-shrink: 0; gap: 8px; }
.video-actions button { background: var(--surface-2); border: 1px solid var(--border); border-radius: 999px; color: var(--text); cursor: pointer; font: inherit; font-size: .84rem; font-weight: 700; padding: 9px 13px; }
.video-actions button:hover, .video-actions button.is-active { background: color-mix(in srgb, var(--brand) 18%, var(--surface)); border-color: color-mix(in srgb, var(--brand) 60%, var(--border)); color: var(--brand); }
.local-action-note { color: var(--muted); font-size: .78rem; margin: 10px 0 0; text-align: right; }
.author-panel { align-items: center; border-bottom: 1px solid var(--border); border-top: 1px solid var(--border); display: flex; gap: 12px; margin-top: 22px; padding: 16px 0; }
.author-panel__avatar { align-items: center; background: color-mix(in srgb, var(--brand) 32%, var(--surface-2)); border-radius: 50%; color: var(--text); display: inline-flex; flex: 0 0 auto; font-weight: 800; height: 42px; justify-content: center; width: 42px; }
.author-panel__name { font-weight: 750; margin: 0; }.author-panel__bio { color: var(--muted); font-size: .86rem; line-height: 1.45; margin: 3px 0 0; }
.video-description { line-height: 1.7; margin: 20px 0 0; white-space: pre-line; }
.watch-layout__aside { min-width: 0; }.watch-layout__aside h2 { font-size: 1.12rem; margin: 2px 0 16px; }
.recommendations-list { display: grid; gap: 16px; }.recommendations-error, .recommendations-empty { background: var(--surface-2); border-radius: 10px; color: var(--muted); font-size: .88rem; margin: 0; padding: 15px; }.recommendations-error { color: var(--danger); }.recommendations-error p { margin: 0; }.recommendations-retry { background: var(--brand-action); border: 0; border-radius: 999px; color: var(--on-brand); font-weight: 700; margin-top: 12px; padding: 7px 13px; }
@media (max-width: 1000px) { .watch-layout { grid-template-columns: minmax(0, 1fr); }.watch-layout__aside { border-top: 1px solid var(--border); padding-top: 24px; } }
@media (max-width: 600px) { .video-details__heading { flex-direction: column; }.video-actions { width: 100%; }.video-actions button { flex: 1; }.local-action-note { text-align: left; }.watch-player { border-radius: 10px; }.video-details { padding-top: 16px; } }
</style>
