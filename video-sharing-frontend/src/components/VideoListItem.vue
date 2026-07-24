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
const publishDate = computed(() => props.video?.createdAt ?? props.video?.uploadDate ?? props.video?.date)
</script>

<template>
  <article class="video-list-item">
    <RouterLink class="video-list-item__thumbnail" :to="`/video/${video.id}`" :aria-label="`打开视频：${video.title || '未命名视频'}`">
      <img
        v-if="shouldShowImage"
        :src="thumbnailUrl"
        :alt="`${video.title || '视频'} 的缩略图`"
        @error="imageFailed = true"
      >
      <div v-else class="video-list-item__placeholder" data-testid="thumbnail-placeholder" aria-label="视界视频占位">
        <span>视界</span>
        <small>VIDEO</small>
      </div>
    </RouterLink>

    <div class="video-list-item__body">
      <RouterLink class="video-list-item__title" :to="`/video/${video.id}`">
        {{ video.title || '未命名视频' }}
      </RouterLink>
      <p class="video-list-item__author">{{ authorName }}</p>
      <p class="video-list-item__meta">{{ formatViews(video.views) }} <span aria-hidden="true">·</span> {{ formatDate(publishDate) }}</p>
    </div>
  </article>
</template>

<style scoped>
.video-list-item { display: grid; gap: 12px; grid-template-columns: 154px minmax(0, 1fr); }
.video-list-item__thumbnail { aspect-ratio: 16 / 9; background: var(--surface-2); border-radius: 10px; display: block; overflow: hidden; }
.video-list-item__thumbnail img { height: 100%; object-fit: cover; width: 100%; }
.video-list-item__placeholder { align-items: center; background: linear-gradient(135deg, color-mix(in srgb, var(--brand) 25%, var(--surface-2)), var(--surface-2)); color: var(--text); display: flex; flex-direction: column; font-size: .86rem; font-weight: 800; height: 100%; justify-content: center; letter-spacing: .14em; }
.video-list-item__placeholder small { color: var(--muted); font-size: .55rem; letter-spacing: .24em; margin-top: 2px; }
.video-list-item__body { min-width: 0; padding: 2px 0; }
.video-list-item__title { color: var(--text); display: -webkit-box; font-size: .95rem; font-weight: 750; line-height: 1.4; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.video-list-item__title:hover { color: var(--brand); }
.video-list-item__author, .video-list-item__meta { color: var(--muted); font-size: .78rem; margin: 7px 0 0; }
.video-list-item__meta { font-size: .72rem; }
@media (max-width: 560px) { .video-list-item { grid-template-columns: 132px minmax(0, 1fr); } }
</style>
