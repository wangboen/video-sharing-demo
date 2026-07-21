<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import api from '../services/api';
import { clearAuth, getStoredUser } from '../services/auth';

const router = useRouter();
const user = ref(null);

onMounted(() => {
  const storedUser = getStoredUser();
  if (storedUser) {
    user.value = storedUser;
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
@media (max-width: 760px) { .channel-hero { padding: 32px 26px; }.channel-card { grid-template-columns: auto 1fr; }.channel-actions { grid-column: 1 / -1; justify-content: flex-start; } }
</style>
