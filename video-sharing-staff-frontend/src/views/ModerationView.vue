<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import api from '../services/api'
import { runLeaseHeartbeat } from '../services/leaseHeartbeat'
import LeaseRail from '../components/LeaseRail.vue'

const QUEUE_LIMIT = 20

const task = ref(null)
const queue = ref([])
const videoUrl = ref('')
const thumbnailUrl = ref('')
const loadingQueue = ref(false)
const claiming = ref(false)
const selectingId = ref(null)
const releasing = ref(false)
const deciding = ref(false)
const emptyQueue = ref(false)
const rejectReason = ref('')
const message = ref('')
const messageKind = ref('status')
let heartbeatTimer = null
let currentLeaseToken = null
const heartbeatFlights = new Map()

const actionLocked = computed(() => claiming.value || selectingId.value !== null || releasing.value || deciding.value)

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function setMessage(copy, kind = 'status') {
  message.value = copy
  messageKind.value = kind
}

function clearMedia() {
  videoUrl.value = ''
  thumbnailUrl.value = ''
}

function stopHeartbeatTimer() {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function isCurrentLease(token) {
  return currentLeaseToken === token && task.value !== null
}

function clearTask(expectedToken = currentLeaseToken) {
  if (expectedToken && currentLeaseToken !== expectedToken) return false
  stopHeartbeatTimer()
  currentLeaseToken = null
  clearMedia()
  task.value = null
  rejectReason.value = ''
  return true
}

function heartbeat(leaseToken = currentLeaseToken) {
  if (!isCurrentLease(leaseToken)) return Promise.resolve()
  if (heartbeatFlights.has(leaseToken)) return heartbeatFlights.get(leaseToken)
  const activeId = task.value.id

  const operation = runLeaseHeartbeat({
    request: () => api.post(`/api/moderation/videos/${activeId}/heartbeat`),
    isCurrent: () => isCurrentLease(leaseToken),
    onSuccess: ({ data }) => {
      task.value = {
        ...task.value,
        version: data.version,
        leaseExpiresAt: data.leaseExpiresAt,
      }
    },
    onConflict: () => {
      clearTask(leaseToken)
      setMessage('租约已发生变化，此任务已从本地清除，请重新选择。', 'error')
      loadQueue()
    },
    onFailure: () => {
      setMessage('租约续期失败。任务仍保留，可继续重试；最终状态以服务器为准。', 'error')
    },
  }).finally(() => {
    heartbeatFlights.delete(leaseToken)
  })

  heartbeatFlights.set(leaseToken, operation)
  return operation
}

function startHeartbeat(leaseToken = currentLeaseToken) {
  stopHeartbeatTimer()
  if (!isCurrentLease(leaseToken)) return
  heartbeatTimer = window.setInterval(() => heartbeat(leaseToken), 60_000)
}

async function prepareLeaseMutation(leaseToken) {
  stopHeartbeatTimer()
  const pendingHeartbeat = heartbeatFlights.get(leaseToken)
  if (pendingHeartbeat) await pendingHeartbeat
  return isCurrentLease(leaseToken)
}

async function applyTask(nextTask) {
  clearTask()
  const leaseToken = { videoId: nextTask.id }
  currentLeaseToken = leaseToken
  task.value = nextTask
  videoUrl.value = nextTask.videoUrl || ''
  thumbnailUrl.value = nextTask.thumbnailUrl || ''
  emptyQueue.value = false
  queue.value = queue.value.filter((item) => item.id !== nextTask.id)
  setMessage('任务已领取，请在租约时间内完成审核。')
  startHeartbeat(leaseToken)
}

async function loadQueue() {
  if (loadingQueue.value) return
  loadingQueue.value = true
  try {
    const { data } = await api.get('/api/moderation/videos/queue', { params: { limit: QUEUE_LIMIT } })
    queue.value = Array.isArray(data) ? data : []
    emptyQueue.value = queue.value.length === 0 && !task.value
  } catch {
    if (!message.value) {
      setMessage('待审核队列暂时无法加载，请稍后重试。', 'error')
    }
  } finally {
    loadingQueue.value = false
  }
}

async function claimNext() {
  if (claiming.value || task.value || deciding.value) return
  claiming.value = true
  emptyQueue.value = false
  message.value = ''
  try {
    const response = await api.post('/api/moderation/videos/claim-next')
    if (response.status === 204 || !response.data) {
      emptyQueue.value = true
      setMessage('队列已检查完毕。')
      await loadQueue()
      return
    }
    await applyTask(response.data)
  } catch {
    setMessage('领取失败，请检查网络后重试。', 'error')
  } finally {
    claiming.value = false
  }
}

async function claimSelected(item) {
  if (!item || task.value || actionLocked.value) return
  selectingId.value = item.id
  emptyQueue.value = false
  message.value = ''
  try {
    const { data } = await api.post(`/api/moderation/videos/${item.id}/claim`)
    await applyTask(data)
  } catch (error) {
    if (error.response?.status === 409) {
      setMessage('该视频已被其他审核员领取，队列已刷新。', 'error')
      await loadQueue()
    } else {
      setMessage('领取失败，请检查网络后重试。', 'error')
    }
  } finally {
    selectingId.value = null
  }
}

async function releaseTask() {
  if (!task.value || actionLocked.value) return
  const leaseToken = currentLeaseToken
  releasing.value = true
  try {
    if (!await prepareLeaseMutation(leaseToken)) return
    const activeId = task.value.id
    await api.delete(`/api/moderation/videos/${activeId}/claim`)
    if (!isCurrentLease(leaseToken)) return
    clearTask(leaseToken)
    setMessage('任务已释放，可以选择下一条。')
    await loadQueue()
  } catch (error) {
    if (!isCurrentLease(leaseToken)) return
    if (error.response?.status === 409) {
      clearTask(leaseToken)
      setMessage('任务已发生变化，本地任务已清除，请重新选择。', 'error')
      await loadQueue()
    } else {
      setMessage('释放失败，任务仍保留；网络中断时服务器租约会自动到期。', 'error')
    }
  } finally {
    if (isCurrentLease(leaseToken)) startHeartbeat(leaseToken)
    releasing.value = false
  }
}

async function decide(decision) {
  if (!task.value || deciding.value || releasing.value) return
  const reason = rejectReason.value.trim()
  if (decision === 'REJECT' && !reason) {
    setMessage('请输入驳回原因。', 'error')
    return
  }
  if (decision === 'REJECT' && reason.length > 500) {
    setMessage('驳回原因不能超过 500 个字符。', 'error')
    return
  }

  const leaseToken = currentLeaseToken
  deciding.value = true
  message.value = ''

  try {
    if (!await prepareLeaseMutation(leaseToken)) return
    const activeTask = task.value
    const payload = {
      decision,
      ...(decision === 'REJECT' ? { reason } : {}),
      version: activeTask.version,
    }
    await api.patch(`/api/moderation/videos/${activeTask.id}`, payload)
    if (!isCurrentLease(leaseToken)) return
    clearTask(leaseToken)
    setMessage('审核决定已提交，可以选择下一条。')
    await loadQueue()
  } catch (error) {
    if (!isCurrentLease(leaseToken)) return
    if (error.response?.status === 409) {
      clearTask(leaseToken)
      setMessage('任务已发生变化，本地内容已清除，请重新选择。', 'error')
      await loadQueue()
    } else {
      setMessage('提交失败，当前任务仍保留，请确认后重试。', 'error')
    }
  } finally {
    if (isCurrentLease(leaseToken)) startHeartbeat(leaseToken)
    deciding.value = false
  }
}

onMounted(loadQueue)
onBeforeUnmount(clearTask)
</script>

<template>
  <main class="page moderation-page">
    <header class="page-heading moderation-heading">
      <div>
        <p class="eyebrow">内容安全 / MODERATION</p>
        <h1>审核工作台</h1>
        <p>队列由系统自动生成，审核员可从待审视频中自由选择。</p>
      </div>
      <div class="moderation-heading__actions">
        <button
          class="button button--quiet"
          type="button"
          data-test="refresh-queue"
          :disabled="loadingQueue"
          @click="loadQueue"
        >
          {{ loadingQueue ? '刷新中...' : '刷新队列' }}
        </button>
        <button
          class="button button--primary"
          type="button"
          data-test="claim"
          :disabled="claiming || !!task || deciding"
          @click="claimNext"
        >
          {{ claiming ? '正在领取...' : '领取下一条' }}
        </button>
      </div>
    </header>

    <p
      v-if="message"
      class="message"
      :class="{ 'message--error': messageKind === 'error' }"
      :role="messageKind === 'error' ? 'alert' : 'status'"
      aria-live="polite"
    >
      {{ message }}
    </p>

    <section v-if="queue.length || loadingQueue" class="queue-panel" aria-labelledby="queue-title">
      <div class="section-heading">
        <div>
          <h2 id="queue-title">待审核队列</h2>
          <p class="muted">进行中的任务置顶，其余按上传时间优先。</p>
        </div>
        <span class="count-chip telemetry">{{ queue.length }} READY</span>
      </div>
      <p v-if="loadingQueue && !queue.length" class="inline-state" role="status">正在加载待审核队列...</p>
      <div v-else class="queue-list">
        <button
          v-for="item in queue"
          :key="item.id"
          class="queue-item"
          type="button"
          :data-test="`queue-item-${item.id}`"
          :disabled="!!task || actionLocked"
          @click="claimSelected(item)"
        >
          <span>
            <strong>{{ item.title || '未命名视频' }}</strong>
            <small>{{ item.uploader }} · {{ formatDate(item.uploadDate) }}</small>
          </span>
          <span class="telemetry">{{ selectingId === item.id ? 'CLAIMING' : `VIDEO / ${item.id}` }}</span>
        </button>
      </div>
    </section>

    <section v-if="emptyQueue && !task" class="directional-empty" data-test="empty">
      <span class="directional-empty__arrow" aria-hidden="true">-></span>
      <div>
        <h2>当前没有待审核视频</h2>
        <p>队列可能很快更新。保留此页面，稍后刷新队列或再次领取。</p>
      </div>
    </section>

    <section v-if="task" class="moderation-cockpit" aria-label="当前审核任务">
      <div class="preview-stage">
        <div class="preview-stage__topline">
          <span>安全预览</span>
          <span class="telemetry">VIDEO / {{ task.id }}</span>
        </div>
        <div class="preview-frame">
          <video
            v-if="videoUrl"
            :src="videoUrl"
            :poster="thumbnailUrl || undefined"
            controls
            preload="metadata"
          >
            浏览器不支持视频播放。
          </video>
          <div v-else class="preview-loading" role="status">正在加载受保护预览...</div>
        </div>
        <div class="preview-caption">
          <div>
            <span>上传者</span>
            <strong>{{ task.uploader }}</strong>
          </div>
          <div>
            <span>上传时间</span>
            <strong class="telemetry">{{ formatDate(task.uploadDate) }}</strong>
          </div>
        </div>
      </div>

      <aside class="decision-rail">
        <LeaseRail :expires-at="task.leaseExpiresAt" />

        <div class="task-copy">
          <div class="task-copy__telemetry">
            <span class="telemetry">版本 {{ task.version }}</span>
            <span>服务器所有权校验</span>
          </div>
          <h2>{{ task.title }}</h2>
          <p>{{ task.description || '未填写视频说明。' }}</p>
        </div>

        <dl class="task-metadata">
          <div>
            <dt>领取时间</dt>
            <dd class="telemetry">{{ formatDate(task.claimedAt) }}</dd>
          </div>
          <div>
            <dt>租约到期</dt>
            <dd class="telemetry">{{ formatDate(task.leaseExpiresAt) }}</dd>
          </div>
        </dl>

        <div class="decision-controls">
          <label for="reject-reason">驳回原因</label>
          <textarea
            id="reject-reason"
            v-model="rejectReason"
            rows="4"
            maxlength="501"
            placeholder="驳回时必填，最多 500 个字符"
          ></textarea>
          <div class="character-count telemetry">{{ rejectReason.trim().length }} / 500</div>

          <div class="decision-actions">
            <button
              class="button button--approve"
              type="button"
              data-test="approve"
              :disabled="actionLocked"
              @click="decide('APPROVE')"
            >
              通过
            </button>
            <button
              class="button button--reject"
              type="button"
              data-test="reject"
              :disabled="actionLocked"
              @click="decide('REJECT')"
            >
              驳回
            </button>
          </div>
          <button
            class="button button--quiet button--wide"
            type="button"
            data-test="release"
            :disabled="actionLocked"
            @click="releaseTask"
          >
            {{ releasing ? '正在释放...' : '释放任务' }}
          </button>
        </div>
      </aside>
    </section>

    <section v-else-if="!emptyQueue && !queue.length && !loadingQueue" class="workbench-ready">
      <div class="workbench-ready__screen" aria-hidden="true">
        <span></span><span></span><span></span>
      </div>
      <div>
        <p class="eyebrow">READY</p>
        <h2>工作台已就绪</h2>
        <p>选择队列中的视频开始审核。每次选择都会创建或恢复属于你的任务租约。</p>
      </div>
    </section>
  </main>
</template>
