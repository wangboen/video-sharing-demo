<script setup>
import { useRouter } from 'vue-router'
import api from '../services/api'
import { clearStaffSession } from '../services/staffAuth'

const props = defineProps({
  user: {
    type: Object,
    default: null,
  },
})

const router = useRouter()

function staffHome() {
  return props.user?.role === 'ADMIN'
    ? { name: 'reviewers' }
    : { name: 'moderation' }
}

async function logout() {
  try {
    await api.post('/api/auth/logout')
  } catch {
    // Local staff credentials are authoritative for leaving this browser session.
  } finally {
    clearStaffSession()
    await router.push({ name: 'login' })
  }
}
</script>

<template>
  <div class="staff-app">
    <header class="staff-header">
      <RouterLink class="staff-brand" :to="staffHome()" aria-label="镜鉴员工工作台">
        <span class="staff-brand__mark" aria-hidden="true">鉴</span>
        <span>
          <strong>镜鉴</strong>
          <small>STAFF REVIEW</small>
        </span>
      </RouterLink>

      <nav class="staff-nav" aria-label="员工工作台导航">
        <RouterLink v-if="user?.role === 'REVIEWER'" :to="{ name: 'moderation' }">
          审核工作台
        </RouterLink>
        <RouterLink v-if="user?.role === 'ADMIN'" :to="{ name: 'reviewers' }">
          审核员管理
        </RouterLink>
        <RouterLink v-if="user?.role === 'ADMIN'" :to="{ name: 'taxonomy' }">
          分类标签
        </RouterLink>
      </nav>

      <div class="staff-identity">
        <span class="staff-identity__copy">
          <strong>{{ user?.username }}</strong>
          <small>{{ user?.role === 'ADMIN' ? '管理员' : '审核员' }}</small>
        </span>
        <button class="button button--quiet" type="button" data-test="logout" @click="logout">
          退出
        </button>
      </div>
    </header>
    <div class="staff-content">
      <slot />
    </div>
  </div>
</template>
