<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import api from '../services/api';
import { clearAuth, PUBLIC_ACCESS_ERROR, saveAuth, USER_ROLE } from '../services/auth';
import { friendlyApiError } from '../utils/apiError';

const username = ref('');
const password = ref('');
const error = ref('');
const submitting = ref(false);
const router = useRouter();

if (router.currentRoute.value.query.error === 'access-denied') {
  error.value = PUBLIC_ACCESS_ERROR;
}

const handleLogin = async () => {
  if (submitting.value) return;

  error.value = '';
  submitting.value = true;
  try {
    const response = await api.post('/api/auth/login', {
      username: username.value,
      password: password.value,
    });
    if (response.data.role !== USER_ROLE) {
      clearAuth();
      error.value = PUBLIC_ACCESS_ERROR;
      return;
    }
    saveAuth(response.data);
    const redirect = typeof router.currentRoute.value.query.redirect === 'string'
      && router.currentRoute.value.query.redirect.startsWith('/')
      ? router.currentRoute.value.query.redirect
      : '/';
    router.push(redirect);
  } catch (err) {
    error.value = friendlyApiError(err, '登录失败，请稍后重试。');
  } finally {
    submitting.value = false;
  }
};
</script>

<template>
  <section class="auth-page">
    <section class="auth-brand-panel">
      <RouterLink to="/" class="auth-brand">视界</RouterLink>
      <p class="auth-eyebrow">发现与分享</p>
      <h1>每一段热爱，值得被看见。</h1>
      <p>登录视界，继续浏览你的精彩视频与创作灵感。</p>
    </section>
    <section class="auth-form-panel" aria-labelledby="login-title">
      <div class="auth-form-wrap">
        <p class="auth-eyebrow">欢迎回来</p>
        <h2 id="login-title">登录视界</h2>
        <form @submit.prevent="handleLogin">
          <label for="username">用户名</label>
          <input id="username" v-model="username" type="text" maxlength="50" autocomplete="username" required />

          <label for="password">密码</label>
          <input id="password" v-model="password" type="password" autocomplete="current-password" required />

          <button type="submit" :disabled="submitting">{{ submitting ? '正在登录…' : '登录' }}</button>
          <p v-if="error" class="error-message" role="alert">{{ error }}</p>
        </form>
        <p class="auth-switch">还没有账号？<RouterLink to="/register">创建账号</RouterLink></p>
      </div>
    </section>
  </section>
</template>

<style src="../assets/auth.css" scoped></style>
