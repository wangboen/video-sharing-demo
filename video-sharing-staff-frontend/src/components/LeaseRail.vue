<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  expiresAt: {
    type: String,
    required: true,
  },
})

const now = ref(Date.now())
const timer = window.setInterval(() => {
  now.value = Date.now()
}, 1000)

const remaining = computed(() => {
  const milliseconds = Math.max(0, new Date(props.expiresAt).getTime() - now.value)
  const seconds = Math.floor(milliseconds / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  return [hours, minutes, rest].map((part) => String(part).padStart(2, '0')).join(':')
})

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <aside class="lease-rail" aria-label="当前任务租约状态">
    <span class="lease-rail__line" aria-hidden="true"></span>
    <div>
      <span class="lease-rail__label">租约进行中</span>
      <strong class="telemetry">{{ remaining }}</strong>
      <span class="lease-rail__hint">剩余参考时间</span>
    </div>
  </aside>
</template>
