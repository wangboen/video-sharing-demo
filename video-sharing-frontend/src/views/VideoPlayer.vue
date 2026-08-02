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
})

onBeforeUnmount(() => {
  reportWatchProgress('unload', { force: true })
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <section class="video-player-page">
    <LoadingState v-if="loading" :count="3" />
    <ErrorState v-else-if="error" :message="error" @retry="loadPage" />

    <div v-else-if="video" class="watch-layout">
      <section class="watch-layout__main">
        <video
          ref="player"
          class="watch-player"
          controls
          :src="getVideoUrl(video)"
          :poster="getThumbnailUrl(video)"
          @timeupdate="reportWatchProgress('heartbeat')"
          @pause="reportWatchProgress('pause', { force: true })"
          @ended="reportWatchProgress('ended', { force: true })"
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
.comments-section { border-top: 1px solid var(--border); margin-top: 26px; padding-top: 22px; }.comments-section__heading { align-items: center; display: flex; justify-content: space-between; }.comments-section h2 { font-size: 1.2rem; margin: 0; }.comments-section__heading button, .comments-load-more, .comment-item button { background: transparent; border: 1px solid var(--border); border-radius: 8px; color: var(--text); cursor: pointer; font: inherit; font-size: .82rem; padding: 7px 10px; }.comments-group { margin-top: 20px; }.comments-group h3 { font-size: .95rem; margin: 0 0 10px; }.comment-item { border-bottom: 1px solid var(--border); padding: 13px 0; }.comment-item--sending { opacity: .7; }.comment-item__meta { align-items: center; display: flex; gap: 9px; }.comment-item__meta span, .comments-empty, .comments-login-note { color: var(--muted); font-size: .82rem; }.comment-item p { line-height: 1.55; margin: 7px 0 10px; white-space: pre-line; }.comment-item button[aria-pressed="true"] { border-color: var(--brand); color: var(--brand); }.comment-item button:disabled { cursor: progress; }.comment-composer { display: grid; gap: 10px; margin-top: 18px; }.comment-composer textarea { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; color: var(--text); font: inherit; min-height: 78px; padding: 10px; resize: vertical; }.comment-composer button { background: var(--brand-action); border: 0; border-radius: 8px; color: var(--on-brand); cursor: pointer; font: inherit; font-weight: 700; justify-self: end; padding: 8px 13px; }.comment-composer button:disabled { cursor: not-allowed; opacity: .6; }.comments-error { color: var(--danger); margin: 12px 0 0; }.comments-load-more { display: block; margin: 16px auto 0; }
.watch-layout__aside { min-width: 0; }.watch-layout__aside h2 { font-size: 1.12rem; margin: 2px 0 16px; }
.recommendations-list { display: grid; gap: 16px; }.recommendations-error, .recommendations-empty { background: var(--surface-2); border-radius: 10px; color: var(--muted); font-size: .88rem; margin: 0; padding: 15px; }.recommendations-error { color: var(--danger); }.recommendations-error p { margin: 0; }.recommendations-retry { background: var(--brand-action); border: 0; border-radius: 999px; color: var(--on-brand); font-weight: 700; margin-top: 12px; padding: 7px 13px; }
@media (max-width: 1000px) { .watch-layout { grid-template-columns: minmax(0, 1fr); }.watch-layout__aside { border-top: 1px solid var(--border); padding-top: 24px; } }
@media (max-width: 600px) { .video-details__heading { flex-direction: column; }.video-actions { width: 100%; }.video-actions button { flex: 1; }.local-action-note { text-align: left; }.watch-player { border-radius: 10px; }.video-details { padding-top: 16px; } }
</style>
