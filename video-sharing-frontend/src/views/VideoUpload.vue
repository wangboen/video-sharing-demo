<script setup>
import { onBeforeUnmount, ref } from 'vue';
import api from '../services/api';
import { friendlyApiError } from '../utils/apiError';

const title = ref('');
const description = ref('');
const selectedFile = ref(null);
const selectedThumbnail = ref(null);
const thumbnailPreview = ref('');
const error = ref('');
const successMessage = ref('');
const uploading = ref(false);
const videoInput = ref(null);
const thumbnailInput = ref(null);

const revokeThumbnailPreview = () => {
  if (thumbnailPreview.value) {
    URL.revokeObjectURL(thumbnailPreview.value);
    thumbnailPreview.value = '';
  }
};

const handleFileChange = (event) => {
  selectedFile.value = event.target.files[0] || null;
};

const handleThumbnailChange = (event) => {
  const file = event.target.files[0];
  revokeThumbnailPreview();
  selectedThumbnail.value = file || null;
  thumbnailPreview.value = file ? URL.createObjectURL(file) : '';
};

const handleUpload = async () => {
  if (uploading.value) return;

  error.value = '';
  successMessage.value = '';

  if (!selectedFile.value) {
    error.value = '请选择要上传的视频文件。';
    return;
  }

  uploading.value = true;
  try {
    const formData = new FormData();
    formData.append('title', title.value);
    formData.append('description', description.value);
    formData.append('file', selectedFile.value);
    if (selectedThumbnail.value) {
      formData.append('thumbnail', selectedThumbnail.value);
    }

    await api.post('/api/videos/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });

    successMessage.value = '视频已提交审核';
    title.value = '';
    description.value = '';
    selectedFile.value = null;
    selectedThumbnail.value = null;
    revokeThumbnailPreview();
    if (videoInput.value) videoInput.value.value = '';
    if (thumbnailInput.value) thumbnailInput.value.value = '';
  } catch (err) {
    error.value = friendlyApiError(err, '视频发布失败，请稍后重试。');
  } finally {
    uploading.value = false;
  }
};

onBeforeUnmount(revokeThumbnailPreview);
</script>

<template>
  <section class="upload-page">
    <section class="upload-intro">
      <p class="section-kicker">创作工作室</p>
      <h1>发布新视频</h1>
      <p>准备好标题、简介和封面，让每一次分享都有清晰的表达。</p>
      <dl class="upload-notes">
        <div><dt>01</dt><dd>选择你要发布的视频文件</dd></div>
        <div><dt>02</dt><dd>添加醒目的封面与内容简介</dd></div>
        <div><dt>03</dt><dd>发布后可在视频列表中查看</dd></div>
      </dl>
    </section>

    <section class="upload-studio" aria-labelledby="upload-form-title">
      <div class="upload-studio__heading">
        <p class="section-kicker">新作品</p>
        <h2 id="upload-form-title">填写发布信息</h2>
      </div>
      <form @submit.prevent="handleUpload">
        <div class="form-field form-field--wide">
          <label for="file">选择视频文件</label>
          <input id="file" ref="videoInput" type="file" accept="video/*" required @change="handleFileChange" />
          <span>{{ selectedFile ? selectedFile.name : '支持常见视频格式' }}</span>
        </div>

        <div class="form-field">
          <label for="title">视频标题</label>
          <input id="title" v-model="title" type="text" required placeholder="为作品起一个好标题" />
        </div>

        <div class="form-field">
          <label for="thumbnail">上传封面</label>
          <input id="thumbnail" ref="thumbnailInput" type="file" accept="image/jpeg,image/png,image/webp" @change="handleThumbnailChange" />
          <img v-if="thumbnailPreview" :src="thumbnailPreview" alt="视频封面预览" class="thumbnail-preview" />
        </div>

        <div class="form-field form-field--wide">
          <label for="description">视频简介</label>
          <textarea id="description" v-model="description" placeholder="说说这段视频的故事、亮点或灵感。"></textarea>
        </div>

        <div class="upload-actions form-field--wide">
          <button type="submit" :disabled="uploading">{{ uploading ? '正在发布…' : '立即发布' }}</button>
          <p v-if="error" class="error-message" role="alert">{{ error }}</p>
          <p v-if="successMessage" class="success-message" role="status">
            {{ successMessage }}，<RouterLink to="/dashboard">查看我的投稿</RouterLink>
          </p>
        </div>
      </form>
    </section>
  </section>
</template>

<style scoped>
.upload-page { display: grid; grid-template-columns: minmax(260px, .72fr) minmax(440px, 1.08fr); gap: clamp(28px, 5vw, 72px); max-width: 1180px; margin: 0 auto; padding: clamp(36px, 6vw, 84px) 24px; }
.upload-intro { padding: 28px 0; }
.section-kicker { margin: 0; color: var(--brand); font-size: .78rem; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }
.upload-intro h1, .upload-studio h2 { margin: 10px 0 14px; color: var(--text); letter-spacing: -.04em; }
.upload-intro h1 { font-size: clamp(2.4rem, 5vw, 4.4rem); line-height: 1.04; }
.upload-intro > p:not(.section-kicker) { max-width: 360px; margin: 0; color: var(--muted); line-height: 1.8; }
.upload-notes { display: grid; gap: 18px; margin: 42px 0 0; }
.upload-notes div { display: flex; gap: 14px; align-items: center; }
.upload-notes dt { color: var(--brand); font-size: 1.15rem; font-weight: 800; }
.upload-notes dd { margin: 0; color: var(--muted); }
.upload-studio { padding: clamp(24px, 4vw, 44px); border: 1px solid var(--border); border-radius: 22px; background: var(--surface); box-shadow: 0 24px 55px color-mix(in srgb, var(--text) 8%, transparent); }
.upload-studio h2 { font-size: 1.75rem; }
.upload-studio form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; margin-top: 30px; }
.form-field { display: grid; gap: 8px; }
.form-field--wide { grid-column: 1 / -1; }
.form-field label { color: var(--text); font-size: .92rem; font-weight: 750; }
.form-field input, .form-field textarea { box-sizing: border-box; width: 100%; border: 1px solid var(--border); border-radius: 10px; padding: 11px 12px; background: var(--surface-2); color: var(--text); font: inherit; outline-color: var(--brand); }
.form-field input[type='file'] { background: var(--surface-2); }
.form-field span { color: var(--muted); font-size: .82rem; }
.form-field textarea { min-height: 116px; resize: vertical; }
.thumbnail-preview { width: 100%; max-height: 180px; border-radius: 10px; object-fit: cover; }
.upload-actions { padding-top: 4px; }
.upload-actions button { width: 100%; border: 0; border-radius: 10px; padding: 13px 18px; background: var(--brand-action); color: var(--on-brand); font: inherit; font-weight: 750; cursor: pointer; }
.upload-actions button:disabled { cursor: wait; opacity: .65; }
.error-message, .success-message { margin: 12px 0 0; }
.error-message { color: var(--danger); }.success-message { color: var(--success); }.success-message a { color: inherit; font-weight: 700; }
@media (max-width: 760px) { .upload-page { grid-template-columns: 1fr; } .upload-intro { padding-top: 0; } .upload-studio form { grid-template-columns: 1fr; } .form-field--wide { grid-column: auto; } }
</style>
