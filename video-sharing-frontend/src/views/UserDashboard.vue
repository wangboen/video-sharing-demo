<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import api from '../services/api';
import { clearAuth, getStoredUser } from '../services/auth';
import { friendlyApiError } from '../utils/apiError';
import { formatDate } from '../utils/video';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingState from '../components/LoadingState.vue';

const router = useRouter();
const user = ref(null);
const videos = ref([]);
const loadingVideos = ref(false);
const videosError = ref('');
const downloadError = ref('');
const activeDownload = ref('');
const currentPage = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const comments = ref([]);
const loadingComments = ref(false);
const commentsError = ref('');
const watchHistory = ref([]);
const loadingWatchHistory = ref(false);
const watchHistoryError = ref('');
const SUBMISSION_PAGE_SIZE = 6;
const WATCH_HISTORY_PAGE_SIZE = 6;

const statusLabels = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
};

const getStatusLabel = (status) => statusLabels[status] || status;

const formatDownloadUntil = (value) => (value ? formatDate(value) : '');

const downloadFile = async (video, file) => {
  activeDownload.value = `${video.id}-${file}`;
  downloadError.value = '';
  try {
    const response = await api.get(`/api/users/me/videos/${video.id}/download/${file}`, {
      params: { redirect: false },
    });
    if (!response.data?.url) {
      throw new Error('Missing download URL');
    }
    window.location.assign(response.data.url);
  } catch (err) {
    downloadError.value = friendlyApiError(err, '下载失败，请稍后重试。');
  } finally {
    activeDownload.value = '';
  }
};

const loadVideos = async (page = currentPage.value) => {
  loadingVideos.value = true;
  videosError.value = '';
  try {
    const response = await api.get('/api/users/me/videos', {
      params: { page, size: SUBMISSION_PAGE_SIZE },
    });
    if (Array.isArray(response.data)) {
      videos.value = response.data;
      currentPage.value = 0;
      totalPages.value = response.data.length ? 1 : 0;
      totalElements.value = response.data.length;
    } else {
      videos.value = Array.isArray(response.data?.content) ? response.data.content : [];
      currentPage.value = Number.isInteger(response.data?.number) ? response.data.number : page;
      totalPages.value = Number.isInteger(response.data?.totalPages) ? response.data.totalPages : 0;
      totalElements.value = Number.isInteger(response.data?.totalElements)
        ? response.data.totalElements
        : videos.value.length;
    }
  } catch (err) {
    videosError.value = friendlyApiError(err, '投稿加载失败，请稍后重试。');
  } finally {
    loadingVideos.value = false;
  }
};

const loadSubmissionPage = (page) => {
  if (loadingVideos.value || page < 0 || (totalPages.value && page >= totalPages.value)) {
    return;
  }
  loadVideos(page);
};

const loadComments = async () => {
  loadingComments.value = true;
  commentsError.value = '';
  try {
    const response = await api.get('/api/users/me/comments');
    comments.value = Array.isArray(response.data) ? response.data : (response.data?.content || []);
  } catch (err) {
    commentsError.value = friendlyApiError(err, '评论加载失败，请稍后重试。');
  } finally {
    loadingComments.value = false;
  }
};

const loadWatchHistory = async () => {
  loadingWatchHistory.value = true;
  watchHistoryError.value = '';
  try {
    const response = await api.get('/api/users/me/watch-history', {
      params: { page: 0, size: WATCH_HISTORY_PAGE_SIZE },
    });
    watchHistory.value = Array.isArray(response.data)
      ? response.data
      : (Array.isArray(response.data?.content) ? response.data.content : []);
  } catch (err) {
    watchHistoryError.value = friendlyApiError(err, 'Watch history failed to load.');
  } finally {
    loadingWatchHistory.value = false;
  }
};

const formatProgress = (record) => {
  const progress = Number(record?.progressPercent);
  if (Number.isFinite(progress)) return `${Math.round(progress)}%`;
  const duration = Number(record?.durationSeconds) || 0;
  const position = Number(record?.lastPositionSeconds) || 0;
  return duration > 0 ? `${Math.round((position / duration) * 100)}%` : '0%';
};

const anonymizeComment = async (commentId) => {
  try {
    await api.delete(`/api/users/me/comments/${commentId}`);
    comments.value = comments.value.filter((comment) => comment.commentId !== commentId);
  } catch (err) {
    commentsError.value = friendlyApiError(err, '评论删除失败，请稍后重试。');
  }
};

onMounted(() => {
  const storedUser = getStoredUser();
  if (storedUser) {
    user.value = storedUser;
    loadVideos();
    loadComments();
    loadWatchHistory();
  } else {
    router.push('/login');
  }
});

const handleLogout = async () => {
  try {
    await api.post('/api/auth/logout');
  } catch {
    // The local session must still be removed when the token is already invalid.
  } finally {
    clearAuth();
    user.value = null;
    router.push('/login');
  }
};
</script>

<template>
  <section class="channel-page">
    <section class="channel-hero">
      <p class="channel-kicker">个人空间</p>
      <h1>我的频道</h1>
      <p>管理你的创作旅程，随时发布下一段精彩。</p>
    </section>
    <section class="channel-card">
      <div class="channel-avatar" aria-hidden="true">{{ user?.username?.slice(0, 1) || '视' }}</div>
      <div class="channel-profile">
        <p class="channel-kicker">创作者</p>
        <h2>{{ user?.username || '视界用户' }}</h2>
        <p>{{ user?.email || '暂未填写邮箱' }}</p>
      </div>
      <div class="channel-actions">
        <RouterLink to="/upload" class="channel-primary">发布视频</RouterLink>
        <RouterLink to="/videos" class="channel-secondary">浏览视频</RouterLink>
        <button type="button" @click="handleLogout">退出登录</button>
      </div>
    </section>
    <section class="submissions-section" aria-labelledby="submissions-title">
      <div class="submissions-heading">
        <div>
          <p class="channel-kicker">创作记录</p>
          <h2 id="submissions-title">我的投稿</h2>
        </div>
        <RouterLink to="/upload" class="channel-primary">上传新视频</RouterLink>
      </div>

      <p v-if="downloadError" class="submission-download-error" role="alert">{{ downloadError }}</p>
      <LoadingState v-if="loadingVideos" :count="3" />
      <ErrorState v-else-if="videosError" :message="videosError" @retry="loadVideos" />
      <EmptyState
        v-else-if="!videos.length"
        title="还没有投稿"
        description="上传第一支视频后，可以在这里查看审核进度。"
        action-to="/upload"
        action-label="上传视频"
      />
      <div v-else class="submission-grid">
        <article v-for="video in videos" :key="video.id" class="submission-card">
          <div class="submission-card__heading">
            <h3>{{ video.title }}</h3>
            <span class="submission-status" :class="`submission-status--${video.status?.toLowerCase()}`">{{ getStatusLabel(video.status) }}</span>
          </div>
          <p v-if="video.status === 'REJECTED' && video.rejectionReason?.trim()" class="submission-reason">驳回原因：{{ video.rejectionReason.trim() }}</p>
          <p v-if="video.downloadAvailableUntil && (video.canDownloadVideo || video.canDownloadThumbnail)" class="submission-download-note">
            可下载至 {{ formatDownloadUntil(video.downloadAvailableUntil) }}
          </p>
          <div v-if="video.canDownloadVideo || video.canDownloadThumbnail" class="submission-downloads">
            <button
              v-if="video.canDownloadVideo"
              class="submission-download"
              type="button"
              :disabled="activeDownload === `${video.id}-video`"
              @click="downloadFile(video, 'video')"
            >
              下载视频
            </button>
            <button
              v-if="video.canDownloadThumbnail"
              class="submission-download submission-download--secondary"
              type="button"
              :disabled="activeDownload === `${video.id}-thumbnail`"
              @click="downloadFile(video, 'thumbnail')"
            >
              下载封面
            </button>
          </div>
          <p v-else-if="video.status === 'REJECTED'" class="submission-expired">下载已过期</p>
          <dl class="submission-times">
            <div><dt>上传时间</dt><dd>{{ formatDate(video.uploadDate) }}</dd></div>
            <div v-if="video.reviewedAt"><dt>审核时间</dt><dd>{{ formatDate(video.reviewedAt) }}</dd></div>
          </dl>
        </article>
      </div>
      <nav v-if="totalPages > 1" class="submission-pagination" aria-label="投稿分页">
        <button
          class="submission-page-prev"
          type="button"
          :disabled="currentPage <= 0 || loadingVideos"
          @click="loadSubmissionPage(currentPage - 1)"
        >
          上一页
        </button>
        <span>{{ currentPage + 1 }} / {{ totalPages }} · {{ totalElements }}</span>
        <button
          class="submission-page-next"
          type="button"
          :disabled="currentPage >= totalPages - 1 || loadingVideos"
          @click="loadSubmissionPage(currentPage + 1)"
        >
          下一页
        </button>
      </nav>
    </section>
    <section class="watch-history-section" aria-labelledby="watch-history-title">
      <div class="submissions-heading"><div><p class="channel-kicker">Watch history</p><h2 id="watch-history-title">Continue watching</h2></div></div>
      <p v-if="watchHistoryError" class="submission-download-error" role="alert">{{ watchHistoryError }}</p>
      <LoadingState v-if="loadingWatchHistory" :count="2" />
      <p v-else-if="!watchHistory.length" class="personal-comments-empty">No watch history yet.</p>
      <div v-else class="watch-history-list">
        <RouterLink
          v-for="record in watchHistory"
          :key="record.video?.id"
          class="watch-history-item"
          :to="`/videos/${record.video?.id}`"
        >
          <div>
            <h3>{{ record.video?.title || 'Untitled video' }}</h3>
            <span>{{ formatDate(record.lastWatchedAt || record.video?.uploadDate) }}</span>
          </div>
          <strong>{{ formatProgress(record) }}</strong>
        </RouterLink>
      </div>
    </section>
    <section class="personal-comments-section" aria-labelledby="personal-comments-title">
      <div class="submissions-heading"><div><p class="channel-kicker">互动记录</p><h2 id="personal-comments-title">我的评论</h2></div></div>
      <p v-if="commentsError" class="submission-download-error" role="alert">{{ commentsError }}</p>
      <LoadingState v-if="loadingComments" :count="2" />
      <p v-else-if="!comments.length" class="personal-comments-empty">还没有发表过评论。</p>
      <div v-else class="personal-comments-list"><article v-for="comment in comments" :key="comment.commentId" class="personal-comment"><div><p>{{ comment.content }}</p><span>{{ formatDate(comment.createdAt) }}</span></div><button class="personal-comment-delete" type="button" @click="anonymizeComment(comment.commentId)">删除</button></article></div>
    </section>
  </section>
</template>

<style scoped>
.channel-page { max-width: 1040px; margin: 0 auto; padding: clamp(36px, 6vw, 80px) 24px; }
.channel-hero { padding: 44px; border-radius: 24px; color: var(--on-brand); background: radial-gradient(circle at 86% 20%, color-mix(in srgb, var(--brand-warm) 65%, transparent) 0, transparent 25%), linear-gradient(125deg, color-mix(in srgb, var(--brand-action) 72%, var(--surface)), var(--brand-action)); }
.channel-kicker { margin: 0; color: var(--brand); font-size: .78rem; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }.channel-hero .channel-kicker { color: var(--on-brand-muted); }
.channel-hero h1 { margin: 10px 0; font-size: clamp(2.3rem, 5vw, 4rem); letter-spacing: -.05em; }.channel-hero p:last-child { margin: 0; color: var(--on-brand-muted); }
.channel-card { display: grid; grid-template-columns: auto 1fr auto; gap: 24px; align-items: center; margin-top: 24px; padding: 30px; border: 1px solid var(--border); border-radius: 20px; background: var(--surface); box-shadow: 0 18px 42px color-mix(in srgb, var(--text) 7%, transparent); }
.channel-avatar { display: grid; width: 68px; height: 68px; place-items: center; border-radius: 50%; background: color-mix(in srgb, var(--brand) 22%, var(--surface-2)); color: var(--text); font-size: 1.75rem; font-weight: 800; }.channel-profile h2 { margin: 7px 0; color: var(--text); font-size: 1.5rem; }.channel-profile p:last-child { margin: 0; color: var(--muted); }
.channel-actions { display: flex; flex-wrap: wrap; gap: 10px; justify-content: flex-end; }.channel-actions a, .channel-actions button { padding: 10px 14px; border-radius: 9px; font: inherit; font-weight: 700; text-decoration: none; cursor: pointer; }.channel-primary { background: var(--brand-action); color: var(--on-brand); }.channel-secondary { border: 1px solid var(--border); color: var(--text); }.channel-actions button { border: 0; background: transparent; color: var(--danger); }
.submissions-section { margin-top: 42px; }.submissions-heading { align-items: center; display: flex; gap: 20px; justify-content: space-between; margin-bottom: 18px; }.submissions-heading h2 { color: var(--text); margin: 6px 0 0; }.submissions-heading a { border-radius: 9px; font-weight: 700; padding: 10px 14px; text-decoration: none; }.submission-grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); }.submission-card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 20px; }.submission-card__heading { align-items: start; display: flex; gap: 12px; justify-content: space-between; }.submission-card h3 { color: var(--text); margin: 0; }.submission-status { border-radius: 999px; font-size: .8rem; font-weight: 750; padding: 5px 9px; white-space: nowrap; }.submission-status--pending { background: color-mix(in srgb, var(--brand-warm) 22%, var(--surface)); color: var(--text); }.submission-status--approved { background: color-mix(in srgb, var(--success) 18%, var(--surface)); color: var(--success); }.submission-status--rejected { background: color-mix(in srgb, var(--danger) 16%, var(--surface)); color: var(--danger); }.submission-reason { color: var(--danger); line-height: 1.55; margin: 14px 0; }.submission-download-note { color: var(--muted); font-size: .9rem; margin: 12px 0 0; }.submission-downloads { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; }.submission-download { border: 0; border-radius: 9px; background: var(--brand-action); color: var(--on-brand); cursor: pointer; font: inherit; font-weight: 750; padding: 8px 12px; text-decoration: none; }.submission-download:disabled { cursor: progress; opacity: .72; }.submission-download--secondary { background: transparent; border: 1px solid var(--border); color: var(--text); }.submission-download-error { color: var(--danger); font-weight: 700; margin: 0 0 16px; }.submission-expired { color: var(--muted); font-weight: 700; margin: 14px 0 0; }.submission-times { display: grid; gap: 7px; margin: 16px 0 0; }.submission-times div { display: flex; gap: 8px; }.submission-times dt { color: var(--muted); }.submission-times dd { color: var(--text); margin: 0; }.submission-pagination { align-items: center; display: flex; gap: 12px; justify-content: center; margin-top: 22px; }.submission-pagination button { border: 1px solid var(--border); border-radius: 9px; background: var(--surface); color: var(--text); cursor: pointer; font: inherit; font-weight: 750; padding: 8px 12px; }.submission-pagination button:disabled { cursor: not-allowed; opacity: .55; }.submission-pagination span { color: var(--muted); font-weight: 700; }
.watch-history-section, .personal-comments-section { border-top: 1px solid var(--border); margin-top: 42px; padding-top: 28px; }.watch-history-list { display: grid; gap: 10px; }.watch-history-item { align-items: center; border: 1px solid var(--border); border-radius: 12px; color: var(--text); display: flex; gap: 18px; justify-content: space-between; padding: 14px 16px; text-decoration: none; }.watch-history-item h3 { margin: 0 0 5px; font-size: 1rem; }.watch-history-item span { color: var(--muted); font-size: .84rem; }.watch-history-item strong { color: var(--brand); font-size: .95rem; }.personal-comments-list { display: grid; gap: 10px; }.personal-comment { align-items: center; border-bottom: 1px solid var(--border); display: flex; gap: 18px; justify-content: space-between; padding: 13px 0; }.personal-comment p { margin: 0 0 5px; }.personal-comment span, .personal-comments-empty { color: var(--muted); font-size: .84rem; }.personal-comment-delete { background: transparent; border: 1px solid var(--border); border-radius: 8px; color: var(--danger); cursor: pointer; font: inherit; padding: 7px 10px; }
@media (max-width: 760px) { .channel-hero { padding: 32px 26px; }.channel-card { grid-template-columns: auto 1fr; }.channel-actions { grid-column: 1 / -1; justify-content: flex-start; } }
</style>
