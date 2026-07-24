<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import StaffShell from './components/StaffShell.vue'
import { readStaffSession } from './services/staffAuth'

const route = useRoute()
const isLogin = computed(() => route.name === 'login')
const staffUser = computed(() => {
  route.fullPath
  return readStaffSession()?.user ?? null
})
</script>

<template>
  <RouterView v-if="isLogin" />
  <StaffShell v-else :user="staffUser">
    <RouterView />
  </StaffShell>
</template>
