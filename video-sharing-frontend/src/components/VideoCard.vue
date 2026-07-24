<script setup>
import { computed, ref } from 'vue'
import { formatDate, formatViews, getAuthorName, getThumbnailUrl } from '../utils/video'

const props = defineProps({
  video: {
    type: Object,
    required: true,
  },
})

const imageFailed = ref(false)
const thumbnailUrl = computed(() => getThumbnailUrl(props.video))
const shouldShowImage = computed(() => Boolean(thumbnailUrl.value) && !imageFailed.value)
const authorName = computed(() => getAuthorName(props.video) || '匿名用户')
const authorInitial = computed(() => authorName.value.trim().charAt(0).toUpperCase() || '视')
const publishDate = computed(() => props.video?.createdAt ?? props.video?.uploadDate ?? props.video?.date)
</script>

<template>
  <article class="video-card">
    <RouterLink class="video-card__thumbnail" :to="`/video/${video.id}`" :aria-label="`打开视频：${video.title || '未命名视频'}`">
      <img
        v-if="shouldShowImage"
        :src="thumbnailUrl"
        :alt="`${video.title || '视频'} 的缩略图`"
        @error="imageFailed = true"
      >
      <div v-else class="video-card__placeholder" data-testid="thumbnail-placeholder" aria-label="视界视频占位">
        <span>视界</span>
        <small>VIDEO</small>
      </div>
    </RouterLink>

    <div class="video-card__body">
      <RouterLink class="video-card__title" :to="`/video/${video.id}`">
        {{ video.title || '未命名视频' }}
      </RouterLink>
      <div class="video-card__author">
        <span class="video-card__avatar" aria-hidden="true">{{ authorInitial }}</span>
        <span>{{ authorName }}</span>
      </div>
      <p class="video-card__meta">{{ formatViews(video.views) }} <span aria-hidden="true">·</span> {{ formatDate(publishDate) }}</p>
    </div>
  </article>
</template>

<style scoped>
.video-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; transition: border-color 180ms ease, transform 180ms ease; }
.video-card:hover { border-color: color-mix(in srgb, var(--brand) 48%, var(--border)); transform: translateY(-3px); }
.video-card__thumbnail { aspect-ratio: 16 / 9; background: var(--surface-2); display: block; overflow: hidden; }
.video-card__thumbnail img { height: 100%; object-fit: cover; width: 100%; }
.video-card__placeholder { align-items: center; background: linear-gradient(135deg, color-mix(in srgb, var(--brand) 25%, var(--surface-2)), var(--surface-2)); color: var(--text); display: flex; flex-direction: column; font-weight: 800; height: 100%; justify-content: center; letter-spacing: .16em; }
.video-card__placeholder small { color: var(--muted); font-size: .65rem; letter-spacing: .28em; margin-top: 3px; }
.video-card__body { padding: 14px; }
.video-card__title { color: var(--text); display: -webkit-box; font-size: 1rem; font-weight: 750; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.video-card__title:hover { color: var(--brand); }
.video-card__author { align-items: center; color: var(--muted); display: flex; font-size: .82rem; gap: 8px; margin-top: 12px; }
.video-card__avatar { align-items: center; background: color-mix(in srgb, var(--brand) 30%, var(--surface-2)); border-radius: 50%; color: var(--text); display: inline-flex; font-size: .72rem; font-weight: 750; height: 24px; justify-content: center; width: 24px; }
.video-card__meta { color: var(--muted); font-size: .78rem; margin: 8px 0 0; }
</style>
