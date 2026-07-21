<script setup>
import { computed } from 'vue'
import { VIDEO_CATEGORIES } from '../utils/video'

const props = defineProps({
  modelValue: {
    type: String,
    default: '全部',
  },
})

const emit = defineEmits(['update:modelValue'])
const categories = computed(() => Object.keys(VIDEO_CATEGORIES))
</script>

<template>
  <nav class="category-chips" aria-label="视频分类">
    <button
      v-for="category in categories"
      :key="category"
      class="category-chips__item"
      :class="{ 'category-chips__item--active': props.modelValue === category }"
      type="button"
      :aria-pressed="props.modelValue === category"
      @click="emit('update:modelValue', category)"
    >
      {{ category }}
    </button>
  </nav>
</template>

<style scoped>
.category-chips { display: flex; flex-wrap: wrap; gap: 10px; }
.category-chips__item { background: var(--surface); border: 1px solid var(--border); border-radius: 999px; color: var(--muted); padding: 7px 14px; transition: background 160ms ease, border-color 160ms ease, color 160ms ease; }
.category-chips__item:hover { border-color: color-mix(in srgb, var(--brand) 50%, var(--border)); color: var(--text); }
.category-chips__item--active { background: var(--brand-action); border-color: var(--brand-action); color: var(--on-brand); font-weight: 700; }
</style>
