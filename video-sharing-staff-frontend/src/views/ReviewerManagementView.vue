<script setup>
import { onMounted, reactive, ref } from 'vue'
import api from '../services/api'

const reviewers = ref([])
const loading = ref(true)
const creating = ref(false)
const listError = ref('')
const formMessage = ref('')
const formMessageKind = ref('status')
const form = reactive({
  username: '',
  email: '',
  password: '',
})

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function loadReviewers() {
  if (loading.value && reviewers.value.length) return
  loading.value = true
  listError.value = ''
  try {
    const { data } = await api.get('/api/admin/reviewers')
    reviewers.value = Array.isArray(data) ? data : []
  } catch {
    listError.value = '审核员列表暂时无法加载，请重试。'
  } finally {
    loading.value = false
  }
}

function validate() {
  const username = form.username.trim()
  const email = form.email.trim()
  if (username.length < 3 || username.length > 50) {
    return '用户名需为 3–50 个字符。'
  }
  if (email.length > 100) {
    return '邮箱不能超过 100 个字符。'
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return '邮箱格式不正确。'
  }
  if (form.password.length < 8 || form.password.length > 72) {
    return '初始密码需为 8–72 个字符。'
  }
  return ''
}

async function createReviewer() {
  if (creating.value) return
  formMessage.value = validate()
  formMessageKind.value = formMessage.value ? 'error' : 'status'
  if (formMessage.value) return

  creating.value = true
  try {
    const { data } = await api.post('/api/admin/reviewers', {
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
    })
    reviewers.value = [
      data,
      ...reviewers.value.filter((reviewer) => reviewer.id !== data.id),
    ]
    form.username = ''
    form.email = ''
    form.password = ''
    formMessage.value = '审核员已创建。'
    formMessageKind.value = 'status'
  } catch (error) {
    formMessage.value = [400, 409].includes(error.response?.status)
      ? '用户名或邮箱已被使用，请更换后重试。'
      : '创建服务暂时不可用，请保留表单后重试。'
    formMessageKind.value = 'error'
  } finally {
    creating.value = false
  }
}

onMounted(loadReviewers)
</script>

<template>
  <main class="page reviewers-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">权限管理 / ADMIN</p>
        <h1>审核员管理</h1>
        <p>创建专用审核账号，并核对当前审核团队。</p>
      </div>
      <button
        class="button button--quiet"
        type="button"
        data-test="refresh"
        :disabled="loading"
        @click="loadReviewers"
      >
        刷新列表
      </button>
    </header>

    <div class="reviewer-layout">
      <section class="paper-panel create-reviewer" aria-labelledby="create-reviewer-title">
        <p class="eyebrow">新增账号</p>
        <h2 id="create-reviewer-title">创建审核员</h2>
        <form class="form-stack" novalidate @submit.prevent="createReviewer">
          <label for="reviewer-username">用户名</label>
          <input
            id="reviewer-username"
            v-model="form.username"
            minlength="3"
            maxlength="50"
            autocomplete="off"
            required
          >
          <small>3–50 个字符，用于员工登录。</small>

          <label for="reviewer-email">邮箱（选填）</label>
          <input
            id="reviewer-email"
            v-model="form.email"
            type="email"
            maxlength="100"
            autocomplete="off"
          >

          <label for="reviewer-password">初始密码</label>
          <input
            id="reviewer-password"
            v-model="form.password"
            type="password"
            minlength="8"
            maxlength="72"
            autocomplete="new-password"
            required
          >
          <small>8–72 个字符。</small>

          <p
            v-if="formMessage"
            class="message"
            :class="{ 'message--error': formMessageKind === 'error' }"
            :role="formMessageKind === 'error' ? 'alert' : 'status'"
          >
            {{ formMessage }}
          </p>
          <button class="button button--primary button--wide" type="submit" :disabled="creating">
            {{ creating ? '正在创建…' : '创建审核员' }}
          </button>
        </form>
      </section>

      <section class="paper-panel reviewer-directory" aria-labelledby="reviewer-directory-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">团队目录</p>
            <h2 id="reviewer-directory-title">现有审核员</h2>
          </div>
          <span class="count-chip telemetry">{{ reviewers.length }} ACTIVE</span>
        </div>

        <p v-if="loading" class="inline-state" role="status">正在加载审核员…</p>
        <p v-else-if="listError" class="message message--error" role="alert">{{ listError }}</p>
        <div v-else-if="!reviewers.length" class="directory-empty" data-test="empty-reviewers">
          <h3>尚未创建审核员</h3>
          <p>使用左侧表单创建首个专用审核账号。</p>
        </div>
        <div v-else class="reviewer-table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">审核员</th>
                <th scope="col">邮箱</th>
                <th scope="col">角色</th>
                <th scope="col">创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="reviewer in reviewers" :key="reviewer.id">
                <td>
                  <strong>{{ reviewer.username }}</strong>
                  <small class="telemetry">ID {{ reviewer.id }}</small>
                </td>
                <td>{{ reviewer.email || '未填写邮箱' }}</td>
                <td><span class="role-chip telemetry">{{ reviewer.role }}</span></td>
                <td class="telemetry">{{ formatDate(reviewer.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </main>
</template>
