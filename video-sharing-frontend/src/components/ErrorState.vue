<script setup>
defineProps({
  message: { type: String, required: true },
})

defineEmits(['retry'])
</script>

<template>
  <section class="error-state" role="alert">
    <h2>暂时无法加载</h2>
    <p>{{ message }}</p>
    <button type="button" @click="$emit('retry')">重试</button>
  </section>
</template>

<style scoped>
.error-state { background: color-mix(in srgb, var(--brand) 7%, var(--surface)); border: 1px solid color-mix(in srgb, var(--brand) 36%, var(--border)); border-radius: var(--radius-md); padding: 28px; text-align: center; }
h2 { font-size: 1.1rem; margin: 0; } p { color: var(--muted); margin: 8px 0 18px; } button { background: var(--brand-action); border: 0; border-radius: 999px; color: var(--on-brand); font-weight: 700; padding: 8px 17px; }
</style>
