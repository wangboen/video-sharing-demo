<script setup>
const props = defineProps({
  count: {
    type: Number,
    default: 8,
  },
})
</script>

<template>
  <div class="loading-grid" aria-label="正在加载视频" role="status">
    <div v-for="index in props.count" :key="index" class="loading-card" aria-hidden="true">
      <div class="loading-card__thumbnail" />
      <div class="loading-card__line loading-card__line--title" />
      <div class="loading-card__line" />
    </div>
    <span class="sr-only">正在加载视频</span>
  </div>
</template>

<style scoped>
.loading-grid { display: grid; gap: 18px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
.loading-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; padding-bottom: 15px; }
.loading-card__thumbnail, .loading-card__line { animation: pulse 1.25s ease-in-out infinite; background: linear-gradient(90deg, var(--surface-2), color-mix(in srgb, var(--surface-2) 55%, white), var(--surface-2)); background-size: 220% 100%; }
.loading-card__thumbnail { aspect-ratio: 16 / 9; }
.loading-card__line { height: 11px; margin: 14px 14px 0; width: 46%; }
.loading-card__line--title { height: 15px; width: 72%; }
@keyframes pulse { to { background-position: -220% 0; } }
@media (prefers-reduced-motion: reduce) { .loading-card__thumbnail, .loading-card__line { animation: none; } }
</style>
