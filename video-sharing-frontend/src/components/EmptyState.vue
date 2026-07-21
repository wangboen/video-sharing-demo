<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true },
  actionTo: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
})
</script>

<template>
  <section class="empty-state">
    <span class="empty-state__mark" aria-hidden="true">视</span>
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
    <RouterLink v-if="actionTo && actionLabel" class="empty-state__action" :to="actionTo">{{ actionLabel }}</RouterLink>
  </section>
</template>

<style scoped>
.empty-state { align-items: center; background: var(--surface); border: 1px dashed var(--border); border-radius: var(--radius-md); display: flex; flex-direction: column; justify-content: center; min-height: 270px; padding: 32px; text-align: center; }
.empty-state__mark { align-items: center; background: color-mix(in srgb, var(--brand) 20%, var(--surface-2)); border-radius: 14px; color: var(--brand); display: inline-flex; font-size: 1.35rem; font-weight: 800; height: 48px; justify-content: center; width: 48px; }
h2 { font-size: 1.15rem; margin: 16px 0 4px; } p { color: var(--muted); margin: 0; } .empty-state__action { background: var(--brand-action); border-radius: 999px; color: var(--on-brand); margin-top: 18px; padding: 8px 15px; }
</style>
