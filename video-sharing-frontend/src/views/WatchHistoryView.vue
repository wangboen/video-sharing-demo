<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import api from '../services/api'
import { friendlyApiError } from '../utils/apiError'
import { formatDate } from '../utils/video'
import ErrorState from '../components/ErrorState.vue'
import LoadingState from '../components/LoadingState.vue'

const PAGE_SIZE = 20

const records = ref([])
const page = ref(0)
const hasNextPage = ref(true)
const loadingInitial = ref(false)
const loadingMore = ref(false)
const error = ref('')
const loadMoreError = ref('')
const cutoffDate = ref('')
const filtersOpen = ref(false)
const cutoffDateInput = ref(null)
const sentinel = ref(null)
let observer = null

const loading = computed(() => loadingInitial.value || loadingMore.value)
const timelineGroups = computed(() => {
  return records.value.reduce((groups, record) => {
    const timeline = timelineRangeFor(record.lastWatchedAt || record.video?.uploadDate)
    const currentGroup = groups.at(-1)
    if (currentGroup?.timeline.key === timeline.key) {
      currentGroup.records.push(record)
    } else {
      groups.push({ timeline, records: [record] })
    }
    return groups
  }, [])
})

function requestParams(pageNumber) {
  const params = { page: pageNumber, size: PAGE_SIZE }
  if (cutoffDate.value) params.to = cutoffDate.value
  return params
}

function normalizePage(data, pageNumber) {
  if (Array.isArray(data)) {
    return { content: data, hasNext: false }
  }
  const content = Array.isArray(data?.content) ? data.content : []
  const current = Number.isInteger(data?.number) ? data.number : pageNumber
  const totalPages = Number.isInteger(data?.totalPages) ? data.totalPages : current + 1
  return { content, hasNext: current + 1 < totalPages }
}

async function loadPage(pageNumber, append = false) {
  if (append) {
    if (loadingMore.value || !hasNextPage.value) return
    loadingMore.value = true
    loadMoreError.value = ''
  } else {
    loadingInitial.value = true
    error.value = ''
    loadMoreError.value = ''
  }

  try {
    const response = await api.get('/api/users/me/watch-history', {
      params: requestParams(pageNumber),
    })
    const result = normalizePage(response.data, pageNumber)
    records.value = append ? [...records.value, ...result.content] : result.content
    page.value = pageNumber
    hasNextPage.value = result.hasNext
  } catch (err) {
    if (append) loadMoreError.value = friendlyApiError(err, '播放历史加载失败')
    else error.value = friendlyApiError(err, '播放历史加载失败')
  } finally {
    loadingInitial.value = false
    loadingMore.value = false
  }
}

function resetAndLoad() {
  records.value = []
  page.value = 0
  hasNextPage.value = true
  return loadPage(0, false)
}

function loadNextPage() {
  return loadPage(page.value + 1, true)
}

function toDateInputValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function startOfLocalDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function timelineRangeFor(value) {
  const watchedAt = value ? new Date(value) : new Date(0)
  const today = startOfLocalDay(new Date())
  const watchedDay = startOfLocalDay(Number.isNaN(watchedAt.getTime()) ? new Date(0) : watchedAt)
  const diffDays = Math.floor((today.getTime() - watchedDay.getTime()) / 86_400_000)

  if (diffDays <= 0) return { key: 'today', label: '今天' }
  if (diffDays === 1) return { key: 'yesterday', label: '昨天' }
  if (diffDays === 2) return { key: 'before-yesterday', label: '前天' }
  if (diffDays <= 6) return { key: 'within-week', label: '一周内' }
  if (watchedDay.getFullYear() === today.getFullYear() && watchedDay.getMonth() === today.getMonth()) {
    return { key: 'this-month', label: '本月' }
  }
  return { key: 'older-than-month', label: '一月前' }
}

function applyStartOffset(daysAgo) {
  const start = new Date()
  start.setDate(start.getDate() - daysAgo)
  cutoffDate.value = toDateInputValue(start)
}

function clearFilters() {
  cutoffDate.value = ''
}

function openCutoffDatePicker() {
  const input = cutoffDateInput.value
  if (typeof input?.showPicker === 'function') input.showPicker()
  else input?.focus()
}

function formatProgress(record) {
  const progress = Number(record?.progressPercent)
  if (Number.isFinite(progress)) return `${Math.round(progress)}%`
  const duration = Number(record?.durationSeconds) || 0
  const position = Number(record?.lastPositionSeconds) || 0
  return duration > 0 ? `${Math.round((position / duration) * 100)}%` : '0%'
}

onMounted(async () => {
  await resetAndLoad()
  await nextTick()
  if ('IntersectionObserver' in window && sentinel.value) {
    observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        loadNextPage()
      }
    })
    observer.observe(sentinel.value)
  }
})

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})

watch(cutoffDate, () => {
  resetAndLoad()
})
</script>

<template>
  <section class="watch-history-page">
    <header class="watch-history-header">
      <div>
        <p class="watch-history-kicker">Watch history</p>
        <h1>播放历史</h1>
      </div>
      <button
        class="watch-history-filter-toggle"
        data-testid="watch-history-filter-toggle"
        type="button"
        :aria-expanded="filtersOpen"
        @click="filtersOpen = !filtersOpen"
      >{{ filtersOpen ? '收起筛选' : '筛选' }}</button>
      <div v-if="filtersOpen" class="watch-history-filters">
        <div
          class="watch-history-date-control"
          data-testid="watch-history-date-control"
          role="button"
          tabindex="0"
          @click="openCutoffDatePicker"
          @keydown.enter.prevent="openCutoffDatePicker"
          @keydown.space.prevent="openCutoffDatePicker"
        >
          <span>开始时间</span>
          <input ref="cutoffDateInput" v-model="cutoffDate" type="date">
        </div>
        <button class="watch-history-quick" data-testid="filter-yesterday" type="button" @click="applyStartOffset(1)">昨天</button>
        <button class="watch-history-quick" data-testid="filter-before-yesterday" type="button" @click="applyStartOffset(2)">前天</button>
        <button class="watch-history-quick" data-testid="filter-last-week" type="button" @click="applyStartOffset(7)">上周</button>
        <button class="watch-history-clear" type="button" @click="clearFilters">清除筛选</button>
      </div>
    </header>

    <LoadingState v-if="loadingInitial" :count="3" />
    <ErrorState v-else-if="error" :message="error" @retry="resetAndLoad" />
    <p v-else-if="!records.length" class="watch-history-empty">暂无播放历史</p>

    <div v-else class="watch-history-list">
      <div
        v-for="group in timelineGroups"
        :key="group.timeline.key"
        class="watch-history-timeline-group"
        data-testid="history-timeline-group"
      >
        <span class="watch-history-timeline watch-history-timeline--grouped">
          <span
            class="watch-history-timeline__label"
            data-testid="history-timeline-label"
          >{{ group.timeline.label }}</span>
        </span>
        <div class="watch-history-group-records">
          <div
            v-for="record in group.records"
            :key="`${record.video?.id}-${record.lastWatchedAt}`"
            class="watch-history-row"
          >
        <RouterLink
          class="watch-history-record"
          :to="`/video/${record.video?.id}`"
        >
        <span class="watch-history-thumb">
          <img
            v-if="record.video?.thumbnailUrl"
            :src="record.video.thumbnailUrl"
            :alt="record.video?.title || '视频封面'"
            width="160"
            height="90"
            loading="lazy"
          >
          <span v-else class="watch-history-placeholder">无封面</span>
        </span>
        <div>
          <h2>{{ record.video?.title || 'Untitled video' }}</h2>
          <span>{{ formatDate(record.lastWatchedAt || record.video?.uploadDate) }}</span>
        </div>
        <strong>{{ formatProgress(record) }}</strong>
        </RouterLink>
          </div>
        </div>
      </div>
    </div>

    <div ref="sentinel" class="watch-history-sentinel" />
    <p v-if="loadingMore" class="watch-history-bottom">加载中...</p>
    <div v-else-if="loadMoreError" class="watch-history-bottom">
      <span>{{ loadMoreError }}</span>
      <button type="button" @click="loadNextPage">重试</button>
    </div>
    <p v-else-if="records.length && !hasNextPage" class="watch-history-bottom">已显示全部播放历史</p>
  </section>
</template>

<style scoped>
.watch-history-page { margin: 0 auto; max-width: 1040px; padding: clamp(32px, 6vw, 72px) 24px; }
.watch-history-header { align-items: flex-end; display: flex; gap: 24px; justify-content: space-between; margin-bottom: 24px; }
.watch-history-kicker { color: var(--brand); font-size: .78rem; font-weight: 800; letter-spacing: .14em; margin: 0; text-transform: uppercase; }
.watch-history-header h1 { color: var(--text); font-size: clamp(2rem, 4vw, 3rem); margin: 8px 0 0; }
.watch-history-filter-toggle, .watch-history-filters button { border: 1px solid var(--border); border-radius: 9px; background: var(--surface); color: var(--text); cursor: pointer; font: inherit; font-weight: 750; padding: 8px 12px; }
.watch-history-filter-toggle:hover, .watch-history-filters button:hover, .watch-history-date-control:hover { background: var(--surface-2); }
.watch-history-filters { align-items: center; display: flex; flex-wrap: wrap; gap: 10px; justify-content: flex-end; }
.watch-history-date-control { align-items: center; background: var(--surface); border: 1px solid var(--border); border-radius: 9px; color: var(--text); cursor: pointer; display: flex; gap: 10px; padding: 6px 9px; }
.watch-history-date-control span { color: var(--muted); font-size: .78rem; font-weight: 750; white-space: nowrap; }
.watch-history-date-control input { background: transparent; border: 0; color: var(--text); cursor: pointer; font: inherit; min-height: 24px; outline: 0; padding: 0; }
.watch-history-quick { background: var(--brand-action) !important; border-color: var(--brand-action) !important; color: var(--on-brand) !important; }
.watch-history-list { display: grid; gap: 12px; position: relative; }
.watch-history-list::before { background: color-mix(in srgb, var(--brand) 34%, var(--border)); bottom: 0; content: ""; left: 43px; position: absolute; top: 0; width: 2px; }
.watch-history-timeline-group { align-items: stretch; display: grid; gap: 18px; grid-template-columns: 104px minmax(0, 1fr); position: relative; }
.watch-history-group-records { display: grid; gap: 12px; }
.watch-history-row { position: relative; }
.watch-history-record { align-items: center; background: var(--surface); border: 1px solid var(--border); border-radius: 14px; color: var(--text); display: grid; gap: 18px; grid-template-columns: 160px minmax(0, 1fr) auto; padding: 16px 18px; text-decoration: none; }
.watch-history-record:hover { background: var(--surface-2); }
.watch-history-timeline { align-items: center; display: flex; min-height: 124px; padding-left: 58px; position: relative; z-index: 1; }
.watch-history-timeline::after { background: var(--brand-action); border: 3px solid var(--bg); border-radius: 50%; box-shadow: 0 0 0 1px color-mix(in srgb, var(--brand) 35%, transparent); content: ""; height: 10px; left: 38px; position: absolute; top: 50%; transform: translateY(-50%); width: 10px; }
.watch-history-timeline--muted::after { background: var(--border); box-shadow: none; height: 8px; left: 39px; width: 8px; }
.watch-history-timeline__label { background: color-mix(in srgb, var(--brand) 10%, var(--surface)); border: 1px solid color-mix(in srgb, var(--brand) 28%, var(--border)); border-radius: 999px; color: var(--brand); font-size: .78rem; font-weight: 800; line-height: 1; padding: 7px 9px; white-space: nowrap; }
.watch-history-thumb { aspect-ratio: 16 / 9; background: #111; border-radius: 10px; display: block; overflow: hidden; width: 160px; }
.watch-history-thumb img { display: block; height: 100%; object-fit: cover; width: 100%; }
.watch-history-placeholder { align-items: center; color: var(--muted); display: flex; font-size: .86rem; font-weight: 750; height: 100%; justify-content: center; width: 100%; }
.watch-history-record h2 { font-size: 1rem; margin: 0 0 6px; }
.watch-history-record span, .watch-history-empty, .watch-history-bottom { color: var(--muted); }
.watch-history-record strong { color: var(--brand); }
.watch-history-sentinel { height: 1px; }
.watch-history-bottom { align-items: center; display: flex; gap: 10px; justify-content: center; margin: 22px 0 0; }
.watch-history-bottom button { border: 1px solid var(--border); border-radius: 8px; background: var(--surface); color: var(--text); cursor: pointer; font: inherit; padding: 7px 10px; }
@media (max-width: 760px) { .watch-history-header { align-items: flex-start; flex-direction: column; } .watch-history-filters { justify-content: flex-start; } .watch-history-list::before { left: 31px; } .watch-history-row { gap: 12px; grid-template-columns: 78px minmax(0, 1fr); } .watch-history-record { align-items: flex-start; grid-template-columns: minmax(0, 1fr); } .watch-history-timeline { min-height: 100%; padding-left: 44px; } .watch-history-timeline::after { left: 26px; } .watch-history-timeline--muted::after { left: 27px; } .watch-history-timeline__label { font-size: .72rem; padding: 6px 7px; } .watch-history-thumb { width: min(100%, 260px); } .watch-history-record strong { justify-self: flex-start; } }
</style>
