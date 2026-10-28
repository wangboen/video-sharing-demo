<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'
import VideoListItem from '../components/VideoListItem.vue'
import api from '../services/api'
import { getStoredUser } from '../services/auth'
import { friendlyApiError } from '../utils/apiError'
import { formatDate, formatViews, getAuthorName, getThumbnailUrl, getVideoUrl } from '../utils/video'

const route = useRoute()
const video = ref(null)
const recommendations = ref([])
const loading = ref(true)
const recommendationsLoading = ref(true)
const error = ref('')
const recommendationsError = ref('')
const liked = ref(false)
const saved = ref(false)
const featuredComments = ref([])
const comments = ref([])
const commentsCursor = ref('')
const commentsLoading = ref(false)
const commentDraft = ref('')
const commentSubmitting = ref(false)
const commentError = ref('')
const currentUser = ref(getStoredUser())
const player = ref(null)
const watchHistory = ref(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(1)
const isMuted = ref(false)
const isFullscreen = ref(false)
const isWebFullscreen = ref(false)
const showControls = ref(true)
const playbackRate = ref(1)
const showSpeedMenu = ref(false)
const isPiPActive = ref(false)
const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2]
let controlsTimer = null

function togglePlay() {
  if (!player.value) return
  if (player.value.paused) {
    player.value.play()
  } else {
    player.value.pause()
  }
}

function handleTimeUpdate() {
  if (!player.value) return
  currentTime.value = player.value.currentTime
  reportWatchProgress('heartbeat')
}

function handleLoadedMetadata() {
  if (!player.value) return
  duration.value = player.value.duration
}

function handleSeek(e) {
  if (!player.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  const pos = (e.clientX - rect.left) / rect.width
  player.value.currentTime = pos * duration.value
}

function formatTime(seconds) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${m}:${String(s).padStart(2, '0')}`
}

function toggleMute() {
  if (!player.value) return
  isMuted.value = !isMuted.value
  player.value.muted = isMuted.value
}

function changePlaybackRate(rate) {
  if (!player.value) return
  playbackRate.value = rate
  player.value.playbackRate = rate
  showSpeedMenu.value = false
}

async function togglePiP() {
  if (!player.value) return
  try {
    if (document.pictureInPictureElement) {
      await document.exitPictureInPicture()
      isPiPActive.value = false
    } else {
      await player.value.requestPictureInPicture()
      isPiPActive.value = true
    }
  } catch (err) {
    console.error('PiP error:', err)
  }
}

function downloadVideo() {
  if (!video.value) return
  const url = getVideoUrl(video.value)
  const a = document.createElement('a')
  a.href = url
  a.download = video.value.title || 'video'
  a.target = '_blank'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

function toggleWebFullscreen() {
  isWebFullscreen.value = !isWebFullscreen.value
  if (isWebFullscreen.value) {
    document.body.style.overflow = 'hidden'
    if (isFullscreen.value) toggleFullscreen()
  } else {
    document.body.style.overflow = ''
  }
}

function toggleFullscreen() {
  if (!player.value) return
  const container = player.value.parentElement
  if (!document.fullscreenElement) {
    container.requestFullscreen().catch(() => {})
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

function handleFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

function closeMenus(e) {
  if (!e.target.closest('.speed-control')) {
    showSpeedMenu.value = false
  }
}

function handleMouseMove() {
  showControls.value = true
  clearTimeout(controlsTimer)
  if (isPlaying.value) {
    controlsTimer = setTimeout(() => {
      showControls.value = false
    }, 3000)
  }
}

let latestRequestVersion = 0
let latestCommentRequestVersion = 0
let lastProgressReportedAt = 0

const authorName = computed(() => getAuthorName(video.value) || '匿名创作者')
const authorInitial = computed(() => authorName.value.trim().charAt(0).toUpperCase() || '视')
const publishDate = computed(() => video.value?.createdAt ?? video.value?.uploadDate ?? video.value?.date)
const authorBio = computed(() => video.value?.user?.bio || video.value?.user?.description || '这个创作者暂未留下简介。')

function commentAuthor(comment) {
  return comment.anonymized ? '匿名用户' : (comment.username || '匿名用户')
}

async function loadComments(reset = false) {
  const videoId = route.params.id
  if (!videoId || (!reset && commentsLoading.value)) return
  const requestVersion = ++latestCommentRequestVersion
  const isLatestRequest = () => requestVersion === latestCommentRequestVersion
  commentsLoading.value = true
  commentError.value = ''
  if (reset) {
    featuredComments.value = []
    comments.value = []
    commentsCursor.value = ''
  }
  try {
    const response = commentsCursor.value && !reset
      ? await api.get(`/api/videos/${videoId}/comments`, { params: { cursor: commentsCursor.value } })
      : await api.get(`/api/videos/${videoId}/comments`)
    if (!isLatestRequest() || String(route.params.id) !== String(videoId)) return
    const data = response.data || {}
    if (reset) featuredComments.value = Array.isArray(data.featured) ? data.featured : []
    comments.value = reset ? (Array.isArray(data.comments) ? data.comments : []) : [...comments.value, ...(Array.isArray(data.comments) ? data.comments : [])]
    commentsCursor.value = data.nextCursor || ''
  } catch (err) {
    if (isLatestRequest()) commentError.value = friendlyApiError(err, '评论加载失败，请稍后重试。')
  } finally {
    if (isLatestRequest()) commentsLoading.value = false
  }
}

async function submitComment() {
  const content = commentDraft.value.trim()
  if (!content || commentSubmitting.value) return
  commentSubmitting.value = true
  commentError.value = ''
  try {
    const response = await api.post(`/api/videos/${route.params.id}/comments`, { content })
    comments.value.unshift({ commentId: response.data.commentId, username: currentUser.value?.username || '我', content, status: 'sending', likeCount: 0, likedByMe: false })
    commentDraft.value = ''
  } catch (err) {
    commentError.value = friendlyApiError(err, '评论发送失败，请稍后重试。')
  } finally {
    commentSubmitting.value = false
  }
}

async function toggleCommentLike(comment) {
  if (comment.status === 'sending') return
  const previouslyLiked = Boolean(comment.likedByMe)
  const previousCount = Number(comment.likeCount) || 0
  comment.likedByMe = !previouslyLiked
  comment.likeCount = Math.max(0, previousCount + (previouslyLiked ? -1 : 1))
  try {
    if (previouslyLiked) await api.delete(`/api/comments/${comment.commentId}/like`)
    else await api.post(`/api/comments/${comment.commentId}/like`)
  } catch {
    comment.likedByMe = previouslyLiked
    comment.likeCount = previousCount
  }
}

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
  } catch (err) {
    if (isLatestRequest()) recommendationsError.value = friendlyApiError(err, '推荐视频加载失败，请稍后重试。')
  } finally {
    if (isLatestRequest()) recommendationsLoading.value = false
  }
}

function retryRecommendations() {
  return loadRecommendations(latestRequestVersion, route.params.id)
}

async function loadWatchHistory(videoId, isLatestRequest) {
  if (!currentUser.value) return
  try {
    const response = await api.get(`/api/users/me/watch-history/${videoId}`)
    if (!isLatestRequest() || String(route.params.id) !== String(videoId)) return
    watchHistory.value = response.data || null
    const lastPosition = Number(watchHistory.value?.lastPositionSeconds) || 0
    if (!watchHistory.value?.completed && lastPosition > 0) {
      await nextTick()
      if (player.value) player.value.currentTime = lastPosition
    }
  } catch {
    if (isLatestRequest()) watchHistory.value = null
  }
}

function progressPayload(eventType) {
  const duration = Number.isFinite(player.value?.duration) ? Math.floor(player.value.duration) : 0
  const position = Number.isFinite(player.value?.currentTime) ? Math.floor(player.value.currentTime) : 0
  return {
    positionSeconds: Math.max(0, position),
    durationSeconds: Math.max(0, duration),
    completed: eventType === 'ended' || (duration > 0 && position / duration >= 0.95),
    eventType,
  }
}

function hasMeaningfulProgress(payload) {
  return payload.positionSeconds >= 10
    || (payload.durationSeconds > 0 && payload.positionSeconds / payload.durationSeconds >= 0.05)
}

function reportWatchProgress(eventType, { force = false } = {}) {
  if (!currentUser.value || !video.value || !player.value) return
  const payload = progressPayload(eventType)
  if (payload.positionSeconds <= 0) return
  if (!force && !hasMeaningfulProgress(payload)) return
  const now = Date.now()
  if (!force && now - lastProgressReportedAt < 30000) return
  lastProgressReportedAt = now
  api.post(`/api/videos/${video.value.id}/watch-progress`, payload).catch(() => {})
}

function handleVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    reportWatchProgress('hidden', { force: true })
  }
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
    watchHistory.value = null
    lastProgressReportedAt = 0
    featuredComments.value = []
    comments.value = []
    commentsCursor.value = ''
    commentDraft.value = ''
    commentError.value = ''
  }

  try {
    const response = await api.get('/api/videos')
    if (!isLatestRequest()) return
    const list = Array.isArray(response.data) ? response.data : []
    const currentVideo = list.find((item) => String(item.id) === String(videoId))
    if (!currentVideo) {
      error.value = 'Video not found.'
      return
    }
    video.value = currentVideo
    recommendations.value = list
      .filter((item) => String(item.id) !== String(videoId))
      .slice(0, 12)
    loadComments(true)
    loadWatchHistory(videoId, isLatestRequest)
  } catch (err) {
    if (isLatestRequest()) error.value = friendlyApiError(err, '视频加载失败，请稍后重试。')
  } finally {
    if (isLatestRequest()) {
      loading.value = false
      recommendationsLoading.value = false
    }
  }
  return

  const currentVideoRequest = api.get('/api/videos')
    .then((response) => {
      if (isLatestRequest()) video.value = response.data
    })
    .catch((err) => {
      if (isLatestRequest()) error.value = friendlyApiError(err, '视频加载失败，请稍后重试。')
    })
    .finally(() => {
      if (isLatestRequest()) loading.value = false
    })

  const recommendationsRequest = loadRecommendations(requestVersion, videoId)

  await Promise.all([currentVideoRequest, recommendationsRequest])
}

watch(() => route.params.id, loadPage, { immediate: true })

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('click', closeMenus)
  if (player.value) {
    player.value.addEventListener('enterpictureinpicture', () => { isPiPActive.value = true })
    player.value.addEventListener('leavepictureinpicture', () => { isPiPActive.value = false })
  }
})

onBeforeUnmount(() => {
  reportWatchProgress('unload', { force: true })
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('click', closeMenus)
  document.body.style.overflow = ''
  clearTimeout(controlsTimer)
})
</script>

<template>
  <section class="video-player-page">
    <LoadingState v-if="loading" :count="3" />
    <ErrorState v-else-if="error" :message="error" @retry="loadPage" />

    <div v-else-if="video" class="watch-layout">
      <section class="watch-layout__main">
        <div class="video-container" :class="{ 'is-web-fullscreen': isWebFullscreen }" @mousemove="handleMouseMove" @mouseleave="showControls = false">
          <video
            ref="player"
            class="watch-player"
            :src="getVideoUrl(video)"
            :poster="getThumbnailUrl(video)"
            @timeupdate="handleTimeUpdate"
            @loadedmetadata="handleLoadedMetadata"
            @play="isPlaying = true"
            @pause="isPlaying = false"
            @ended="reportWatchProgress('ended', { force: true })"
            @click="togglePlay"
          />
          <div class="video-controls" :class="{ 'video-controls--hidden': !showControls && isPlaying }">
            <div class="progress-bar" @click="handleSeek">
              <div class="progress-bar__total"></div>
              <div class="progress-bar__current" :style="{ width: (duration > 0 ? (currentTime / duration * 100) : 0) + '%' }"></div>
            </div>
            <div class="controls-main">
              <div class="controls-left">
                <button class="control-btn" type="button" aria-label="播放/暂停" @click="togglePlay">
                  <svg v-if="!isPlaying" viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M8 5v14l11-7z"/></svg>
                  <svg v-else viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
                </button>
                <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
              </div>
              <div class="controls-right">
                <button class="control-btn" type="button" aria-label="静音/取消静音" @click="toggleMute">
                  <svg v-if="isMuted" viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M3.63 3.63L2.22 5.04L7 9.83v5.17h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81l2.04 2.04l1.41-1.41L3.63 3.63zM12 4L9.91 6.09L12 8.18V4zM16.5 12c0-1.77-1.02-3.29-2.5-4.03v1.85l2.48 2.48c.01-.1.02-.2.02-.3zM19 12c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71z"/></svg>
                  <svg v-else viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>
                </button>

                <div class="speed-control">
                  <button class="control-btn speed-btn" type="button" title="播放速度" @click="showSpeedMenu = !showSpeedMenu">
                    {{ playbackRate === 1 ? '倍速' : playbackRate + 'x' }}
                  </button>
                  <div v-if="showSpeedMenu" class="speed-menu">
                    <button v-for="rate in speeds" :key="rate" :class="{ 'active': playbackRate === rate }" @click="changePlaybackRate(rate)">
                      {{ rate }}x
                    </button>
                  </div>
                </div>

                <button class="control-btn" type="button" title="下载" @click="downloadVideo">
                  <svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"/></svg>
                </button>

                <button class="control-btn" type="button" title="画中画" @click="togglePiP">
                  <svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M19 7h-8v6h8V7zm2-4H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14z"/></svg>
                </button>

                <button class="control-btn" type="button" title="网页全屏" aria-label="网页全屏" @click="toggleWebFullscreen">
                  <svg viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14zM7 10h10v2H7z"/></svg>
                </button>
                <button class="control-btn" type="button" aria-label="全屏" @click="toggleFullscreen">
                  <svg v-if="!isFullscreen" viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
                  <svg v-else viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>
                </button>
              </div>
            </div>
          </div>
        </div>

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

          <div v-if="video.category || video.tags?.length" class="video-taxonomy">
            <span v-if="video.category" class="video-taxonomy__category">{{ video.category.name }}</span>
            <span v-for="tag in video.tags || []" :key="tag.id" class="video-taxonomy__tag">{{ tag.name }}</span>
          </div>

          <div class="author-panel">
            <span class="author-panel__avatar" aria-hidden="true">{{ authorInitial }}</span>
            <div>
              <p class="author-panel__name">{{ authorName }}</p>
              <p class="author-panel__bio">{{ authorBio }}</p>
            </div>
          </div>

          <p v-if="video.description" class="video-description">{{ video.description }}</p>
        </section>
        <section class="comments-section" aria-labelledby="comments-title">
          <div class="comments-section__heading"><h2 id="comments-title">评论</h2><button v-if="commentError" type="button" @click="loadComments(true)">重新加载</button></div>
          <p v-if="commentError" class="comments-error" role="alert">{{ commentError }}</p>
          <div v-if="featuredComments.length" class="comments-group comments-group--featured"><h3>精选评论</h3><article v-for="comment in featuredComments" :key="comment.commentId" class="comment-item"><div class="comment-item__meta"><strong>{{ commentAuthor(comment) }}</strong><span>{{ formatDate(comment.createdAt) }}</span></div><p>{{ comment.content }}</p><button type="button" :aria-pressed="Boolean(comment.likedByMe)" @click="toggleCommentLike(comment)">点赞 {{ comment.likeCount || 0 }}</button></article></div>
          <div class="comments-group"><h3>全部评论</h3><p v-if="!commentsLoading && !comments.length" class="comments-empty">还没有评论。</p><article v-for="comment in comments" :key="comment.commentId" class="comment-item" :class="{ 'comment-item--sending': comment.status === 'sending' }"><div class="comment-item__meta"><strong>{{ commentAuthor(comment) }}</strong><span>{{ comment.status === 'sending' ? '发送中' : formatDate(comment.createdAt) }}</span></div><p>{{ comment.content }}</p><button type="button" :disabled="comment.status === 'sending'" :aria-pressed="Boolean(comment.likedByMe)" @click="toggleCommentLike(comment)">点赞 {{ comment.likeCount || 0 }}</button></article></div>
          <form v-if="currentUser" class="comment-composer" @submit.prevent="submitComment"><textarea v-model="commentDraft" maxlength="1000" placeholder="写下你的评论" aria-label="评论内容" /><button type="submit" :disabled="commentSubmitting || !commentDraft.trim()">发布评论</button></form><p v-else class="comments-login-note">登录后可以发表评论。</p><button v-if="commentsCursor && !commentsLoading" class="comments-load-more" type="button" @click="loadComments()">加载更多</button>
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
.watch-player { aspect-ratio: 16 / 9; background: #000; display: block; height: 100%; width: 100%; object-fit: contain; }
.video-container { aspect-ratio: 16 / 9; background: #000; border-radius: var(--radius-md); position: relative; overflow: hidden; max-height: 80vh; width: 100%; }
.video-container.is-web-fullscreen { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; max-height: none; z-index: 2000; border-radius: 0; aspect-ratio: auto; }
.video-controls {
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  bottom: 0;
  left: 0;
  opacity: 1;
  padding: 10px 20px 15px;
  position: absolute;
  right: 0;
  transition: opacity 0.3s ease;
  width: 100%;
  z-index: 10;
  box-sizing: border-box;
}
.video-controls--hidden { opacity: 0; pointer-events: none; }
.progress-bar { cursor: pointer; height: 4px; margin-bottom: 12px; position: relative; width: 100%; }
.progress-bar:hover { height: 6px; }
.progress-bar__total { background: rgba(255, 255, 255, 0.2); height: 100%; width: 100%; }
.progress-bar__current { background: var(--brand); height: 100%; left: 0; position: absolute; top: 0; }
.controls-main { align-items: center; display: flex; justify-content: space-between; }
.controls-left, .controls-right { align-items: center; display: flex; gap: 15px; }
.control-btn { background: transparent; border: 0; color: #fff; cursor: pointer; padding: 5px; transition: transform 0.1s ease; display: flex; align-items: center; justify-content: center; }
.control-btn:hover { transform: scale(1.1); }
.control-btn svg { filter: drop-shadow(0 0 2px rgba(0, 0, 0, 0.5)); }
.speed-control { position: relative; }
.speed-btn { font-size: 0.8rem; font-weight: 700; min-width: 42px; }
.speed-menu {
  background: rgba(28, 28, 28, 0.95);
  border-radius: 8px;
  bottom: 100%;
  display: flex;
  flex-direction: column-reverse;
  gap: 2px;
  left: 50%;
  margin-bottom: 15px;
  padding: 8px;
  position: absolute;
  transform: translateX(-50%);
  width: 70px;
  z-index: 20;
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.speed-menu button {
  background: transparent;
  border: 0;
  border-radius: 4px;
  color: #fff;
  cursor: pointer;
  font-size: 0.85rem;
  padding: 8px;
  transition: background 0.2s ease;
}
.speed-menu button:hover { background: rgba(255, 255, 255, 0.1); }
.speed-menu button.active { color: var(--brand); font-weight: 700; }
.time-display { color: #fff; font-size: 0.85rem; font-weight: 500; font-family: monospace; text-shadow: 0 0 2px rgba(0, 0, 0, 0.5); }
.video-details { padding: 22px 2px 0; }
.video-details__heading { align-items: flex-start; display: flex; gap: 20px; justify-content: space-between; }
h1 { font-size: clamp(1.45rem, 2.5vw, 2.15rem); letter-spacing: -.045em; line-height: 1.2; margin: 0; }
.video-details__meta { color: var(--muted); font-size: .85rem; margin: 10px 0 0; }
.video-actions { display: flex; flex-shrink: 0; gap: 8px; }
.video-actions button { background: var(--surface-2); border: 1px solid var(--border); border-radius: 999px; color: var(--text); cursor: pointer; font: inherit; font-size: .84rem; font-weight: 700; padding: 9px 13px; }
.video-actions button:hover, .video-actions button.is-active { background: color-mix(in srgb, var(--brand) 18%, var(--surface)); border-color: color-mix(in srgb, var(--brand) 60%, var(--border)); color: var(--brand); }
.local-action-note { color: var(--muted); font-size: .78rem; margin: 10px 0 0; text-align: right; }
.video-taxonomy { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }
.video-taxonomy__category, .video-taxonomy__tag { border-radius: 999px; font-size: .82rem; font-weight: 750; padding: 6px 10px; }
.video-taxonomy__category { background: color-mix(in srgb, var(--brand) 18%, var(--surface-2)); color: var(--brand); }
.video-taxonomy__tag { background: var(--surface-2); color: var(--text); }
.author-panel { align-items: center; border-bottom: 1px solid var(--border); border-top: 1px solid var(--border); display: flex; gap: 12px; margin-top: 22px; padding: 16px 0; }
.author-panel__avatar { align-items: center; background: color-mix(in srgb, var(--brand) 32%, var(--surface-2)); border-radius: 50%; color: var(--text); display: inline-flex; flex: 0 0 auto; font-weight: 800; height: 42px; justify-content: center; width: 42px; }
.author-panel__name { font-weight: 750; margin: 0; }.author-panel__bio { color: var(--muted); font-size: .86rem; line-height: 1.45; margin: 3px 0 0; }
.video-description { line-height: 1.7; margin: 20px 0 0; white-space: pre-line; }
.comments-section { border-top: 1px solid var(--border); margin-top: 26px; padding-top: 22px; }.comments-section__heading { align-items: center; display: flex; justify-content: space-between; }.comments-section h2 { font-size: 1.2rem; margin: 0; }.comments-section__heading button, .comments-load-more, .comment-item button { background: transparent; border: 1px solid var(--border); border-radius: 8px; color: var(--text); cursor: pointer; font: inherit; font-size: .82rem; padding: 7px 10px; }.comments-group { margin-top: 20px; }.comments-group h3 { font-size: .95rem; margin: 0 0 10px; }.comment-item { border-bottom: 1px solid var(--border); padding: 13px 0; }.comment-item--sending { opacity: .7; }.comment-item__meta { align-items: center; display: flex; gap: 9px; }.comment-item__meta span, .comments-empty, .comments-login-note { color: var(--muted); font-size: .82rem; }.comment-item p { line-height: 1.55; margin: 7px 0 10px; white-space: pre-line; }.comment-item button[aria-pressed="true"] { border-color: var(--brand); color: var(--brand); }.comment-item button:disabled { cursor: progress; }.comment-composer { display: grid; gap: 10px; margin-top: 18px; }.comment-composer textarea { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; color: var(--text); font: inherit; min-height: 78px; padding: 10px; resize: vertical; }.comment-composer button { background: var(--brand-action); border: 0; border-radius: 8px; color: var(--on-brand); cursor: pointer; font: inherit; font-weight: 700; justify-self: end; padding: 8px 13px; }.comment-composer button:disabled { cursor: not-allowed; opacity: .6; }.comments-error { color: var(--danger); margin: 12px 0 0; }.comments-load-more { display: block; margin: 16px auto 0; }
.watch-layout__aside { min-width: 0; }.watch-layout__aside h2 { font-size: 1.12rem; margin: 2px 0 16px; }
.recommendations-list { display: grid; gap: 16px; }.recommendations-error, .recommendations-empty { background: var(--surface-2); border-radius: 10px; color: var(--muted); font-size: .88rem; margin: 0; padding: 15px; }.recommendations-error { color: var(--danger); }.recommendations-error p { margin: 0; }.recommendations-retry { background: var(--brand-action); border: 0; border-radius: 999px; color: var(--on-brand); font-weight: 700; margin-top: 12px; padding: 7px 13px; }
@media (max-width: 1000px) { .watch-layout { grid-template-columns: minmax(0, 1fr); }.watch-layout__aside { border-top: 1px solid var(--border); padding-top: 24px; } }
@media (max-width: 600px) { .video-details__heading { flex-direction: column; }.video-actions { width: 100%; }.video-actions button { flex: 1; }.local-action-note { text-align: left; }.watch-player { border-radius: 10px; }.video-details { padding-top: 16px; } }
</style>
