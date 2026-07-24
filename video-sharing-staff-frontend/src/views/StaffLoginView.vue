<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../services/api'
import { isStaffRole, saveStaffSession } from '../services/staffAuth'

const router = useRouter()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  if (submitting.value) return
  errorMessage.value = ''
  submitting.value = true

  try {
    const { data } = await api.post('/api/staff/auth/login', {
      username: username.value.trim(),
      password: password.value,
    })
    if (!isStaffRole(data?.role)) {
      errorMessage.value = '此账号没有员工工作台权限。'
      return
    }
    saveStaffSession(data)
    await router.push({ name: 'moderation' })
  } catch (error) {
    errorMessage.value = error.response?.status === 401
      ? '用户名或密码不正确。'
      : '登录服务暂时不可用，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-context" aria-labelledby="login-context-title">
      <div class="staff-brand staff-brand--light">
        <span class="staff-brand__mark" aria-hidden="true">鉴</span>
        <span>
          <strong>镜鉴</strong>
          <small>STAFF REVIEW</small>
        </span>
      </div>
      <div>
        <p class="eyebrow">视频安全 · 员工入口</p>
        <h1 id="login-context-title">每一次判断，都有清晰依据。</h1>
        <p>领取审核任务，核对内容与上下文，在租约时间内提交明确决定。</p>
      </div>
      <p class="login-context__note">此入口仅供授权审核员与管理员使用。</p>
    </section>

    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-panel__inner">
        <p class="eyebrow">安全登录</p>
        <h2 id="login-title">进入审核工作台</h2>
        <p class="muted">使用员工账号继续。</p>

        <form class="form-stack" @submit.prevent="submit">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="username"
            name="username"
            autocomplete="username"
            required
          >

          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
          >

          <p v-if="errorMessage" class="message message--error" role="alert">
            {{ errorMessage }}
          </p>
          <button class="button button--primary button--wide" type="submit" :disabled="submitting">
            {{ submitting ? '正在验证…' : '登录工作台' }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>
