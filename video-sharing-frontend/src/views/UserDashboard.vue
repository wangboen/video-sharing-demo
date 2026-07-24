<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import api from '../services/api';
import { clearAuth, getStoredUser } from '../services/auth';
import { formatDate } from '../utils/video';
import EmptyState from '../components/EmptyState.vue';
import ErrorState from '../components/ErrorState.vue';
import LoadingState from '../components/LoadingState.vue';

const router = useRouter();
const user = ref(null);
const videos = ref([]);
const loadingVideos = ref(false);
const videosError = ref('');

const statusLabels = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
};

const getStatusLabel = (status) => statusLabels[status] || status;

const loadVideos = async () => {
  loadingVideos.value = true;
  videosError.value = '';
  try {
    const response = await api.get('/api/users/me/videos');
    videos.value = Array.isArray(response.data) ? response.data : [];
  } catch {
    videosError.value = '投稿加载失败，请稍后重试。';
  } finally {
    loadingVideos.value = false;
  }
};

onMounted(() => {
  const storedUser = getStoredUser();
  if (storedUser) {
    user.value = storedUser;
    loadVideos();
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
          <dl class="submission-times">
            <div><dt>上传时间</dt><dd>{{ formatDate(video.uploadDate) }}</dd></div>
            <div v-if="video.reviewedAt"><dt>审核时间</dt><dd>{{ formatDate(video.reviewedAt) }}</dd></div>
          </dl>
        </article>
      </div>
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
.submissions-section { margin-top: 42px; }.submissions-heading { align-items: center; display: flex; gap: 20px; justify-content: space-between; margin-bottom: 18px; }.submissions-heading h2 { color: var(--text); margin: 6px 0 0; }.submissions-heading a { border-radius: 9px; font-weight: 700; padding: 10px 14px; text-decoration: none; }.submission-grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); }.submission-card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 20px; }.submission-card__heading { align-items: start; display: flex; gap: 12px; justify-content: space-between; }.submission-card h3 { color: var(--text); margin: 0; }.submission-status { border-radius: 999px; font-size: .8rem; font-weight: 750; padding: 5px 9px; white-space: nowrap; }.submission-status--pending { background: color-mix(in srgb, var(--brand-warm) 22%, var(--surface)); color: var(--text); }.submission-status--approved { background: color-mix(in srgb, var(--success) 18%, var(--surface)); color: var(--success); }.submission-status--rejected { background: color-mix(in srgb, var(--danger) 16%, var(--surface)); color: var(--danger); }.submission-reason { color: var(--danger); line-height: 1.55; margin: 14px 0; }.submission-times { display: grid; gap: 7px; margin: 16px 0 0; }.submission-times div { display: flex; gap: 8px; }.submission-times dt { color: var(--muted); }.submission-times dd { color: var(--text); margin: 0; }
@media (max-width: 760px) { .channel-hero { padding: 32px 26px; }.channel-card { grid-template-columns: auto 1fr; }.channel-actions { grid-column: 1 / -1; justify-content: flex-start; } }
</style>
