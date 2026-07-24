<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import api from '../services/api'
import LeaseRail from '../components/LeaseRail.vue'

const task = ref(null)
const videoUrl = ref('')
const thumbnailUrl = ref('')
const claiming = ref(false)
const releasing = ref(false)
const deciding = ref(false)
const heartbeatInFlight = ref(false)
const emptyQueue = ref(false)
const rejectReason = ref('')
const message = ref('')
const messageKind = ref('status')
let heartbeatTimer = null

const actionLocked = computed(() => claiming.value || releasing.value || deciding.value)

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (part) => String(part).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function setMessage(copy, kind = 'status') {
  message.value = copy
  messageKind.value = kind
}

function revokeUrl(url) {
  if (url) URL.revokeObjectURL(url)
}

function clearMedia() {
  revokeUrl(videoUrl.value)
  revokeUrl(thumbnailUrl.value)
  videoUrl.value = ''
  thumbnailUrl.value = ''
}

function stopHeartbeat() {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  heartbeatInFlight.value = false
}

function clearTask() {
  stopHeartbeat()
  clearMedia()
  task.value = null
  rejectReason.value = ''
}

async function loadMedia(activeTask) {
  try {
    const videoResponse = await api.get(
      `/api/moderation/videos/${activeTask.id}/preview/video`,
      { responseType: 'blob' },
    )
    if (task.value?.id !== activeTask.id) return
    revokeUrl(videoUrl.value)
    videoUrl.value = URL.createObjectURL(videoResponse.data)

    if (activeTask.hasThumbnail) {
      const thumbnailResponse = await api.get(
        `/api/moderation/videos/${activeTask.id}/preview/thumbnail`,
        { responseType: 'blob' },
      )
      if (task.value?.id !== activeTask.id) return
      revokeUrl(thumbnailUrl.value)
      thumbnailUrl.value = URL.createObjectURL(thumbnailResponse.data)
    }
  } catch {
    if (task.value?.id === activeTask.id) {
      setMessage('媒体预览加载失败，可保留任务后重试或释放任务。', 'error')
    }
  }
}

async function heartbeat() {
  if (!task.value || heartbeatInFlight.value) return
  const activeId = task.value.id
  heartbeatInFlight.value = true
  try {
    const { data } = await api.post(`/api/moderation/videos/${activeId}/heartbeat`)
    if (task.value?.id === activeId) {
      task.value = {
        ...task.value,
        version: data.version,
        leaseExpiresAt: data.leaseExpiresAt,
      }
    }
  } catch (error) {
    if (error.response?.status === 409) {
      clearTask()
      setMessage('租约已发生变化，此任务已从本地清除，请重新领取。', 'error')
    } else if (task.value?.id === activeId) {
      setMessage('租约续期失败。任务仍保留，可继续重试；最终状态以服务器为准。', 'error')
    }
  } finally {
    heartbeatInFlight.value = false
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = window.setInterval(heartbeat, 60_000)
}

async function applyTask(nextTask) {
  clearTask()
  task.value = nextTask
  emptyQueue.value = false
  setMessage('任务已领取，请在租约时间内完成审核。')
  startHeartbeat()
  await loadMedia(nextTask)
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
      return
    }
    await applyTask(response.data)
  } catch {
    setMessage('领取失败，请检查网络后重试。', 'error')
  } finally {
    claiming.value = false
  }
}

async function releaseTask() {
  if (!task.value || actionLocked.value) return
  const activeId = task.value.id
  releasing.value = true
  try {
    await api.delete(`/api/moderation/videos/${activeId}/claim`)
    clearTask()
    setMessage('任务已释放，可以领取下一条。')
  } catch (error) {
    if (error.response?.status === 409) {
      clearTask()
      setMessage('任务已发生变化，本地任务已清除，请重新领取。', 'error')
    } else {
      setMessage('释放失败，任务仍保留；网络中断时服务器租约会自动到期。', 'error')
    }
  } finally {
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

  const activeTask = task.value
  deciding.value = true
  message.value = ''
  const payload = {
    decision,
    ...(decision === 'REJECT' ? { reason } : {}),
    version: activeTask.version,
  }

  try {
    await api.patch(`/api/moderation/videos/${activeTask.id}`, payload)
    clearTask()
    setMessage('审核决定已提交，可以领取下一条。')
  } catch (error) {
    if (error.response?.status === 409) {
      clearTask()
      setMessage('任务已发生变化，本地内容已清除，请重新领取。', 'error')
    } else {
      setMessage('提交失败，当前任务仍保留，请确认后重试。', 'error')
    }
  } finally {
    deciding.value = false
  }
}

onBeforeUnmount(clearTask)
</script>

<template>
  <main class="page moderation-page">
    <header class="page-heading moderation-heading">
      <div>
        <p class="eyebrow">内容安全 / MODERATION</p>
        <h1>审核工作台</h1>
        <p>一次只处理一条任务。租约与版本以服务器响应为准。</p>
      </div>
      <button
        class="button button--primary"
        type="button"
        data-test="claim"
        :disabled="claiming || !!task || deciding"
        @click="claimNext"
      >
        {{ claiming ? '正在领取…' : '领取下一条' }}
      </button>
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

    <section v-if="emptyQueue && !task" class="directional-empty" data-test="empty">
      <span class="directional-empty__arrow" aria-hidden="true">→</span>
      <div>
        <h2>当前没有待审核视频</h2>
        <p>队列可能很快更新。保留此页面，稍后再次领取。</p>
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
          <div v-else class="preview-loading" role="status">正在加载受保护预览…</div>
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
            {{ releasing ? '正在释放…' : '释放任务' }}
          </button>
        </div>
      </aside>
    </section>

    <section v-else-if="!emptyQueue" class="workbench-ready">
      <div class="workbench-ready__screen" aria-hidden="true">
        <span></span><span></span><span></span>
      </div>
      <div>
        <p class="eyebrow">READY</p>
        <h2>工作台已就绪</h2>
        <p>选择“领取下一条”开始审核。每次领取都会创建或恢复属于你的任务租约。</p>
      </div>
    </section>
  </main>
</template>
