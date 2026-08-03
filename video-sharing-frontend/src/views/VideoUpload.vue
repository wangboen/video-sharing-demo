<script setup>
import axios from 'axios';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import api from '../services/api';
import { friendlyApiError } from '../utils/apiError';
import { generateThumbnailFromVideo } from '../utils/videoThumbnail';

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
const categories = ref([]);
const tags = ref([]);
const selectedCategoryId = ref('');
const selectedTagIds = ref([]);
const categoryLoading = ref(false);
const tagLoading = ref(false);

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
  if (!selectedCategoryId.value) {
    error.value = '请选择视频分类。';
    return;
  }

  uploading.value = true;
  let session = null;
  try {
    const effectiveThumbnail = selectedThumbnail.value || await generateThumbnailFromVideo(selectedFile.value);
    const sessionResponse = await api.post('/api/videos/upload-session', {
      title: title.value,
      description: description.value,
      video: {
        fileName: selectedFile.value.name,
        contentType: selectedFile.value.type,
        size: selectedFile.value.size,
      },
      thumbnail: effectiveThumbnail
        ? {
            fileName: effectiveThumbnail.name,
            contentType: effectiveThumbnail.type,
            size: effectiveThumbnail.size,
          }
        : null,
    });
    session = sessionResponse.data;
    await axios.put(session.video.uploadUrl, selectedFile.value, {
      headers: {
        'Content-Type': session.video.contentType,
      },
    });
    if (effectiveThumbnail && session.thumbnail) {
      await axios.put(session.thumbnail.uploadUrl, effectiveThumbnail, {
        headers: {
          'Content-Type': session.thumbnail.contentType,
        },
      });
    }
    await api.post('/api/videos/upload-complete', {
      uploadSessionId: session.uploadSessionId,
      videoKey: session.video.key,
      thumbnailKey: session.thumbnail?.key ?? null,
      title: title.value,
      description: description.value,
      categoryId: Number(selectedCategoryId.value),
      tagIds: selectedTagIds.value.map((id) => Number(id)),
    });

    successMessage.value = '视频已提交审核';
    title.value = '';
    description.value = '';
    selectedFile.value = null;
    selectedThumbnail.value = null;
    selectedCategoryId.value = '';
    selectedTagIds.value = [];
    revokeThumbnailPreview();
    if (videoInput.value) videoInput.value.value = '';
    if (thumbnailInput.value) thumbnailInput.value.value = '';
  } catch (err) {
    if (session?.uploadSessionId && session?.video?.key) {
      try {
        await api.post('/api/videos/upload-cancel', {
          uploadSessionId: session.uploadSessionId,
          videoKey: session.video.key,
          thumbnailKey: session.thumbnail?.key ?? null,
        });
      } catch {
        // Keep the original upload failure visible; cleanup failures are best-effort here.
      }
    }
    error.value = friendlyApiError(err, '视频发布失败，请稍后重试。');
  } finally {
    uploading.value = false;
  }
};

const loadCategories = async () => {
  categoryLoading.value = true;
  try {
    const response = await api.get('/api/categories');
    categories.value = Array.isArray(response.data) ? response.data : [];
  } catch (err) {
    error.value = friendlyApiError(err, '分类加载失败，请稍后重试。');
  } finally {
    categoryLoading.value = false;
  }
};

watch(selectedCategoryId, async (categoryId) => {
  selectedTagIds.value = [];
  tags.value = [];
  if (!categoryId) return;
  tagLoading.value = true;
  try {
    const response = await api.get(`/api/categories/${categoryId}/tags`);
    tags.value = Array.isArray(response.data) ? response.data : [];
  } catch (err) {
    error.value = friendlyApiError(err, '标签加载失败，请稍后重试。');
  } finally {
    tagLoading.value = false;
  }
});

onMounted(loadCategories);
onBeforeUnmount(revokeThumbnailPreview);
</script>

<template>
  <section class="upload-page">
    <section class="upload-intro">
      <p class="section-kicker">创作工作室</p>
      <h1>发布新视频</h1>
      <p>准备好标题和简介；没有封面时，系统会自动从视频生成一张。</p>
      <dl class="upload-notes">
        <div><dt>01</dt><dd>选择你要发布的视频文件</dd></div>
        <div><dt>02</dt><dd>可选上传封面，未上传时自动生成</dd></div>
        <div><dt>03</dt><dd>提交审核后可在我的投稿中查看</dd></div>
      </dl>
    </section>

    <section class="upload-studio" aria-labelledby="upload-form-title">
      <div class="upload-studio__heading">
        <p class="section-kicker">新作品</p>
        <h2 id="upload-form-title">填写发布信息</h2>
      </div>
      <form @submit.prevent="handleUpload">
        <div class="form-field form-field--wide media-file-field">
          <input
            id="file"
            ref="videoInput"
            class="media-file-input"
            type="file"
            accept="video/*"
            required
            @change="handleFileChange"
          />
          <label class="media-file-picker" for="file">
            <span class="media-file-picker__eyebrow">视频文件</span>
            <span class="media-file-picker__action">{{ selectedFile ? '更换视频文件' : '选择视频文件' }}</span>
            <span class="media-file-picker__hint">{{ selectedFile ? selectedFile.name : '支持常见视频格式' }}</span>
          </label>
        </div>

        <div class="form-field">
          <label for="title">视频标题</label>
          <input id="title" v-model="title" type="text" required placeholder="为作品起一个好标题" />
        </div>

        <div class="form-field media-file-field">
          <input
            id="thumbnail"
            ref="thumbnailInput"
            class="media-file-input"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            @change="handleThumbnailChange"
          />
          <label class="media-file-picker" for="thumbnail">
            <span class="media-file-picker__eyebrow">视频封面</span>
            <span class="media-file-picker__action">{{ selectedThumbnail ? '更换封面文件' : '选择封面文件（可选）' }}</span>
            <span class="media-file-picker__hint">{{ selectedThumbnail ? selectedThumbnail.name : '未选择时自动生成，支持 JPG、PNG、WebP' }}</span>
          </label>
          <img v-if="thumbnailPreview" :src="thumbnailPreview" alt="视频封面预览" class="thumbnail-preview" />
        </div>

        <div class="form-field form-field--wide">
          <label for="category">视频分类</label>
          <select id="category" v-model="selectedCategoryId" required :disabled="categoryLoading || uploading">
            <option value="">{{ categoryLoading ? '分类加载中...' : '请选择分类' }}</option>
            <option v-for="category in categories" :key="category.id" :value="String(category.id)">
              {{ category.name }}
            </option>
          </select>
        </div>

        <div class="form-field form-field--wide">
          <span class="tag-field-label">视频标签（可选）</span>
          <p v-if="tagLoading" class="tag-field-hint">标签加载中...</p>
          <p v-else-if="selectedCategoryId && !tags.length" class="tag-field-hint">当前分类暂无可选标签</p>
          <div v-else class="tag-options" aria-label="视频标签">
            <label v-for="tag in tags" :key="tag.id" class="tag-option">
              <input v-model="selectedTagIds" type="checkbox" :value="String(tag.id)" :disabled="uploading">
              <span>{{ tag.name }}</span>
            </label>
          </div>
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
.form-field input, .form-field select, .form-field textarea { box-sizing: border-box; width: 100%; border: 1px solid var(--border); border-radius: 10px; padding: 11px 12px; background: var(--surface-2); color: var(--text); font: inherit; outline-color: var(--brand); }
.form-field input[type='file'] { background: var(--surface-2); }
.form-field span { color: var(--muted); font-size: .82rem; }
.tag-field-label { color: var(--text) !important; font-size: .92rem !important; font-weight: 750; }
.tag-field-hint { color: var(--muted); font-size: .84rem; margin: 0; }
.tag-options { display: flex; flex-wrap: wrap; gap: 9px; }
.tag-option { align-items: center; background: var(--surface-2); border: 1px solid var(--border); border-radius: 999px; cursor: pointer; display: inline-flex; gap: 6px; padding: 7px 11px; }
.tag-option:hover { border-color: color-mix(in srgb, var(--brand) 50%, var(--border)); }
.tag-option input { accent-color: var(--brand-action); height: 14px; padding: 0; width: 14px; }
.tag-option span { color: var(--text); font-size: .84rem; font-weight: 700; }
.media-file-field { position: relative; }
.media-file-field .media-file-input { position: absolute; inset: auto; width: 1px; height: 1px; padding: 0; border: 0; opacity: 0; pointer-events: none; }
.media-file-field .media-file-picker { display: grid; gap: 6px; box-sizing: border-box; width: 100%; min-height: 96px; border: 1px solid color-mix(in srgb, var(--border) 78%, var(--brand) 22%); border-radius: 16px; padding: 16px 18px; background: linear-gradient(135deg, var(--surface-2), color-mix(in srgb, var(--surface-2) 84%, var(--brand) 16%)); cursor: pointer; box-shadow: inset 0 1px 0 color-mix(in srgb, white 42%, transparent); transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease; }
.media-file-field .media-file-picker:hover { border-color: color-mix(in srgb, var(--brand) 48%, var(--border)); box-shadow: 0 12px 28px color-mix(in srgb, var(--brand-action) 12%, transparent), inset 0 1px 0 color-mix(in srgb, white 48%, transparent); transform: translateY(-1px); }
.media-file-field .media-file-input:focus-visible + .media-file-picker { outline: 3px solid color-mix(in srgb, var(--brand) 26%, transparent); outline-offset: 3px; border-color: var(--brand); }
.media-file-field .media-file-picker__eyebrow { color: var(--brand); font-size: .74rem; font-weight: 850; letter-spacing: .12em; text-transform: uppercase; }
.media-file-field .media-file-picker__action { color: var(--text); font-size: 1.04rem; font-weight: 800; }
.media-file-field .media-file-picker__hint { max-width: 100%; overflow: hidden; color: var(--muted); font-size: .84rem; text-overflow: ellipsis; white-space: nowrap; }
.form-field textarea { min-height: 116px; resize: vertical; }
.thumbnail-preview { width: 100%; max-height: 180px; border-radius: 10px; object-fit: cover; }
.upload-actions { padding-top: 4px; }
.upload-actions button { width: 100%; border: 0; border-radius: 10px; padding: 13px 18px; background: var(--brand-action); color: var(--on-brand); font: inherit; font-weight: 750; cursor: pointer; }
.upload-actions button:disabled { cursor: wait; opacity: .65; }
.error-message, .success-message { margin: 12px 0 0; }
.error-message { color: var(--danger); }.success-message { color: var(--success); }.success-message a { color: inherit; font-weight: 700; }
@media (max-width: 760px) { .upload-page { grid-template-columns: 1fr; } .upload-intro { padding-top: 0; } .upload-studio form { grid-template-columns: 1fr; } .form-field--wide { grid-column: auto; } }
</style>
