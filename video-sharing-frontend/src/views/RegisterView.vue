<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import api from '../services/api';
import { saveAuth } from '../services/auth';
import { friendlyApiError } from '../utils/apiError';

const username = ref('');
const email = ref('');
const password = ref('');
const error = ref('');
const submitting = ref(false);
const router = useRouter();

const handleRegister = async () => {
  if (submitting.value) return;

  error.value = '';
  submitting.value = true;
  try {
    const response = await api.post('/api/auth/register', {
      username: username.value,
      email: email.value,
      password: password.value,
    });
    saveAuth(response.data);
    router.push('/');
  } catch (err) {
    error.value = friendlyApiError(err, '注册失败，请稍后重试。');
  } finally {
    submitting.value = false;
  }
};
</script>

<template>
  <section class="auth-page">
    <section class="auth-brand-panel">
      <RouterLink to="/" class="auth-brand">视界</RouterLink>
      <p class="auth-eyebrow">开始创作</p>
      <h1>让你的视角，抵达更多人。</h1>
      <p>创建账号，发现精彩内容，也把属于你的故事分享给世界。</p>
    </section>
    <section class="auth-form-panel" aria-labelledby="register-title">
      <div class="auth-form-wrap">
        <p class="auth-eyebrow">加入视界</p>
        <h2 id="register-title">创建账号</h2>
        <form @submit.prevent="handleRegister">
          <label for="username">用户名</label>
          <input id="username" v-model="username" type="text" minlength="3" maxlength="50" autocomplete="username" required />

          <label for="email">邮箱</label>
          <input id="email" v-model="email" type="email" maxlength="100" autocomplete="email" />

          <label for="password">密码</label>
          <input id="password" v-model="password" type="password" minlength="8" maxlength="72" autocomplete="new-password" required />

          <button type="submit" :disabled="submitting">{{ submitting ? '正在创建…' : '创建账号' }}</button>
          <p v-if="error" class="error-message" role="alert">{{ error }}</p>
        </form>
        <p class="auth-switch">已有账号？<RouterLink to="/login">登录</RouterLink></p>
      </div>
    </section>
  </section>
</template>

<style src="../assets/auth.css" scoped></style>
