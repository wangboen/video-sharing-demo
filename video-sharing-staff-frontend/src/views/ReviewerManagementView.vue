<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '../services/api'

const PAGE_SIZE = 20

const reviewers = ref([])
const reviewerPage = reactive({
  number: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
})
const reviewerSearchInput = ref('')
const reviewerSearch = ref('')
const selectedReviewer = ref(null)
const reviewRecords = ref([])
const reviewPage = reactive({
  number: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
})
const reviewSearchInput = ref('')
const reviewSearch = ref('')
const loading = ref(true)
const loadingReviews = ref(false)
const creating = ref(false)
const listError = ref('')
const reviewError = ref('')
const formMessage = ref('')
const formMessageKind = ref('status')
const form = reactive({
  username: '',
  email: '',
  password: '',
})

const hasReviewerNext = computed(() => reviewerPage.number + 1 < reviewerPage.totalPages)
const hasReviewerPrevious = computed(() => reviewerPage.number > 0)
const hasReviewNext = computed(() => reviewPage.number + 1 < reviewPage.totalPages)
const hasReviewPrevious = computed(() => reviewPage.number > 0)

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function applyReviewerPage(data) {
  reviewers.value = Array.isArray(data?.content) ? data.content : []
  reviewerPage.number = Number.isInteger(data?.number) ? data.number : 0
  reviewerPage.size = Number.isInteger(data?.size) ? data.size : PAGE_SIZE
  reviewerPage.totalElements = Number.isInteger(data?.totalElements) ? data.totalElements : reviewers.value.length
  reviewerPage.totalPages = Number.isInteger(data?.totalPages) ? data.totalPages : (reviewers.value.length ? 1 : 0)
}

function applyReviewPage(data) {
  reviewRecords.value = Array.isArray(data?.content) ? data.content : []
  reviewPage.number = Number.isInteger(data?.number) ? data.number : 0
  reviewPage.size = Number.isInteger(data?.size) ? data.size : PAGE_SIZE
  reviewPage.totalElements = Number.isInteger(data?.totalElements) ? data.totalElements : reviewRecords.value.length
  reviewPage.totalPages = Number.isInteger(data?.totalPages) ? data.totalPages : (reviewRecords.value.length ? 1 : 0)
}

async function loadReviewers(page = reviewerPage.number) {
  if (loading.value && reviewers.value.length) return
  loading.value = true
  listError.value = ''
  try {
    const { data } = await api.get('/api/admin/reviewers', {
      params: { page, size: PAGE_SIZE, q: reviewerSearch.value },
    })
    applyReviewerPage(data)
  } catch {
    listError.value = '审核员列表暂时无法加载，请重试。'
  } finally {
    loading.value = false
  }
}

async function searchReviewers() {
  reviewerSearch.value = reviewerSearchInput.value.trim()
  selectedReviewer.value = null
  reviewRecords.value = []
  await loadReviewers(0)
}

async function loadReviews(page = reviewPage.number) {
  if (!selectedReviewer.value || loadingReviews.value) return
  loadingReviews.value = true
  reviewError.value = ''
  try {
    const { data } = await api.get(`/api/admin/reviewers/${selectedReviewer.value.id}/reviews`, {
      params: { page, size: PAGE_SIZE, q: reviewSearch.value },
    })
    applyReviewPage(data)
  } catch {
    reviewError.value = '审核记录暂时无法加载，请重试。'
  } finally {
    loadingReviews.value = false
  }
}

async function selectReviewer(reviewer) {
  selectedReviewer.value = reviewer
  reviewSearchInput.value = ''
  reviewSearch.value = ''
  reviewRecords.value = []
  await loadReviews(0)
}

async function searchReviews() {
  reviewSearch.value = reviewSearchInput.value.trim()
  await loadReviews(0)
}

function validate() {
  const username = form.username.trim()
  const email = form.email.trim()
  if (username.length < 3 || username.length > 50) {
    return '用户名需为 3-50 个字符。'
  }
  if (email.length > 100) {
    return '邮箱不能超过 100 个字符。'
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return '邮箱格式不正确。'
  }
  if (form.password.length < 8 || form.password.length > 72) {
    return '初始密码需为 8-72 个字符。'
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
    ].slice(0, PAGE_SIZE)
    reviewerPage.totalElements += 1
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

onMounted(() => loadReviewers(0))
</script>

<template>
  <main class="page reviewers-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">权限管理 / ADMIN</p>
        <h1>审核员管理</h1>
        <p>创建专用审核账号，并查看审核员的历史审核表现。</p>
      </div>
      <button
        class="button button--quiet"
        type="button"
        data-test="refresh"
        :disabled="loading"
        @click="loadReviewers(reviewerPage.number)"
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
          <small>3-50 个字符，用于员工登录。</small>

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
          <small>8-72 个字符。</small>

          <p
            v-if="formMessage"
            class="message"
            :class="{ 'message--error': formMessageKind === 'error' }"
            :role="formMessageKind === 'error' ? 'alert' : 'status'"
          >
            {{ formMessage }}
          </p>
          <button class="button button--primary button--wide" type="submit" :disabled="creating">
            {{ creating ? '正在创建...' : '创建审核员' }}
          </button>
        </form>
      </section>

      <section class="paper-panel reviewer-directory" aria-labelledby="reviewer-directory-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">监督总览</p>
            <h2 id="reviewer-directory-title">审核员列表</h2>
          </div>
          <span class="count-chip telemetry">{{ reviewerPage.totalElements }} REVIEWERS</span>
        </div>

        <form class="toolbar-form" data-test="reviewer-search-submit" @submit.prevent="searchReviewers">
          <input
            v-model="reviewerSearchInput"
            data-test="reviewer-search"
            type="search"
            placeholder="搜索用户名或邮箱"
            autocomplete="off"
          >
          <button class="button button--quiet" type="submit">搜索</button>
        </form>

        <p v-if="loading" class="inline-state" role="status">正在加载审核员...</p>
        <p v-else-if="listError" class="message message--error" role="alert">{{ listError }}</p>
        <div v-else-if="!reviewers.length" class="directory-empty" data-test="empty-reviewers">
          <h3>没有匹配的审核员</h3>
          <p>调整搜索词，或使用左侧表单创建新的审核员。</p>
        </div>
        <div v-else class="reviewer-table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">审核员</th>
                <th scope="col">邮箱</th>
                <th scope="col">总数</th>
                <th scope="col">通过</th>
                <th scope="col">驳回</th>
                <th scope="col">最近审核</th>
                <th scope="col">记录</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="reviewer in reviewers"
                :key="reviewer.id"
                :class="{ 'is-selected': selectedReviewer?.id === reviewer.id }"
              >
                <td>
                  <strong>{{ reviewer.username }}</strong>
                  <small class="telemetry">ID {{ reviewer.id }}</small>
                </td>
                <td>{{ reviewer.email || '未填写邮箱' }}</td>
                <td class="telemetry">{{ reviewer.reviewedTotal ?? 0 }}</td>
                <td class="telemetry">{{ reviewer.approvedTotal ?? 0 }}</td>
                <td class="telemetry">{{ reviewer.rejectedTotal ?? 0 }}</td>
                <td class="telemetry">{{ formatDate(reviewer.lastReviewedAt) }}</td>
                <td>
                  <button
                    class="button button--quiet button--compact"
                    type="button"
                    :data-test="`reviews-${reviewer.id}`"
                    @click="selectReviewer(reviewer)"
                  >
                    查看记录
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="pagination-bar">
          <span class="telemetry">PAGE {{ reviewerPage.number + 1 }} / {{ Math.max(reviewerPage.totalPages, 1) }}</span>
          <div>
            <button
              class="button button--quiet button--compact"
              type="button"
              :disabled="!hasReviewerPrevious || loading"
              @click="loadReviewers(reviewerPage.number - 1)"
            >
              上一页
            </button>
            <button
              class="button button--quiet button--compact"
              type="button"
              data-test="reviewers-next"
              :disabled="!hasReviewerNext || loading"
              @click="loadReviewers(reviewerPage.number + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </section>
    </div>

    <section v-if="selectedReviewer" class="paper-panel reviewer-audit" aria-labelledby="reviewer-audit-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">审核记录</p>
          <h2 id="reviewer-audit-title">{{ selectedReviewer.username }}</h2>
        </div>
        <span class="count-chip telemetry">{{ reviewPage.totalElements }} REVIEWS</span>
      </div>

      <form class="toolbar-form" data-test="review-search-submit" @submit.prevent="searchReviews">
        <input
          v-model="reviewSearchInput"
          data-test="review-search"
          type="search"
          placeholder="搜索视频标题、上传者或驳回原因"
          autocomplete="off"
        >
        <button class="button button--quiet" type="submit">搜索记录</button>
      </form>

      <p v-if="loadingReviews" class="inline-state" role="status">正在加载审核记录...</p>
      <p v-else-if="reviewError" class="message message--error" role="alert">{{ reviewError }}</p>
      <div v-else-if="!reviewRecords.length" class="directory-empty" data-test="empty-reviews">
        <h3>没有匹配的审核记录</h3>
        <p>该审核员暂无符合条件的已审核视频。</p>
      </div>
      <div v-else class="reviewer-table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">视频</th>
              <th scope="col">上传者</th>
              <th scope="col">结果</th>
              <th scope="col">审核时间</th>
              <th scope="col">驳回原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in reviewRecords" :key="record.videoId">
              <td>
                <strong>{{ record.title || '未命名视频' }}</strong>
                <small class="telemetry">VIDEO {{ record.videoId }}</small>
              </td>
              <td>{{ record.uploader }}</td>
              <td><span class="role-chip telemetry">{{ record.status }}</span></td>
              <td class="telemetry">{{ formatDate(record.reviewedAt) }}</td>
              <td>{{ record.rejectionReason || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination-bar">
        <span class="telemetry">PAGE {{ reviewPage.number + 1 }} / {{ Math.max(reviewPage.totalPages, 1) }}</span>
        <div>
          <button
            class="button button--quiet button--compact"
            type="button"
            :disabled="!hasReviewPrevious || loadingReviews"
            @click="loadReviews(reviewPage.number - 1)"
          >
            上一页
          </button>
          <button
            class="button button--quiet button--compact"
            type="button"
            :disabled="!hasReviewNext || loadingReviews"
            @click="loadReviews(reviewPage.number + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </section>
  </main>
</template>
