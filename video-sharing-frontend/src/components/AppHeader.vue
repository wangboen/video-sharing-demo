<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useTheme } from '../composables/useTheme'
import BrandLogo from './BrandLogo.vue'

defineProps({
  user: { type: Object, default: null },
  sidebarCollapsed: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle-sidebar'])
const query = ref('')
const router = useRouter()
const { theme, toggleTheme } = useTheme()

const submitSearch = () => {
  router.push({
    name: 'videos',
    query: query.value.trim() ? { q: query.value.trim() } : {},
  })
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__leading">
      <button
        class="icon-button sidebar-toggle"
        type="button"
        aria-label="切换侧边栏"
        :aria-pressed="sidebarCollapsed"
        @click="emit('toggle-sidebar')"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
      </button>
      <RouterLink class="app-header__brand" to="/" aria-label="视界首页"><BrandLogo /></RouterLink>
    </div>

    <form class="app-header__search" role="search" @submit.prevent="submitSearch">
      <label class="sr-only" for="global-search">搜索视频</label>
      <input id="global-search" v-model="query" type="search" placeholder="搜索感兴趣的视频" autocomplete="off">
      <button type="submit" aria-label="搜索">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6" /><path d="m16 16 4 4" /></svg>
      </button>
    </form>

    <div class="app-header__actions">
      <RouterLink class="app-header__upload" to="/upload">上传</RouterLink>
      <button class="icon-button" type="button" :aria-label="theme === 'dark' ? '切换浅色主题' : '切换深色主题'" @click="toggleTheme">
        <svg v-if="theme === 'dark'" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" /></svg>
        <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M20.5 14.1A8.5 8.5 0 0 1 9.9 3.5 8.5 8.5 0 1 0 20.5 14.1Z" /></svg>
      </button>
      <RouterLink v-if="!user" class="app-header__login" to="/login">登录</RouterLink>
      <RouterLink v-else class="app-header__profile" to="/dashboard" aria-label="个人中心">
        <span class="app-header__avatar">{{ user.username?.slice(0, 1) || '我' }}</span>
        <span>{{ user.username || '个人中心' }}</span>
      </RouterLink>
    </div>
  </header>
</template>

<style scoped>
.app-header { align-items: center; background: color-mix(in srgb, var(--bg) 90%, transparent); border-bottom: 1px solid var(--border); display: flex; gap: 28px; height: var(--header-height); justify-content: space-between; left: 0; padding: 0 28px; position: fixed; right: 0; top: 0; z-index: 20; backdrop-filter: blur(18px); }
.app-header__leading, .app-header__actions, .app-header__profile { align-items: center; display: flex; }
.app-header__leading { gap: 16px; min-width: 190px; }
.app-header__brand { display: inline-flex; }
.app-header__search { align-items: center; background: var(--surface-2); border: 1px solid transparent; border-radius: 999px; display: flex; gap: 10px; max-width: 540px; padding: 0 15px; transition: border-color 160ms ease, background 160ms ease; width: min(46vw, 540px); }
.app-header__search:focus-within { background: var(--surface); border-color: color-mix(in srgb, var(--brand) 65%, var(--border)); }
.app-header__search svg { fill: none; height: 18px; stroke: var(--muted); stroke-linecap: round; stroke-width: 1.8; width: 18px; }
.app-header__search input { background: transparent; border: 0; min-width: 0; outline: 0; padding: 10px 0; width: 100%; }
.app-header__search button { align-items: center; align-self: stretch; background: transparent; border: 0; border-left: 1px solid var(--border); color: var(--text); display: inline-flex; justify-content: center; margin-right: -15px; padding: 0 15px; }
.app-header__actions { gap: 12px; min-width: 130px; justify-content: flex-end; }
.icon-button { align-items: center; background: transparent; border: 0; border-radius: 50%; color: var(--text); cursor: pointer; display: inline-flex; height: 40px; justify-content: center; width: 40px; }
.icon-button:hover { background: var(--surface-2); }
.icon-button svg { fill: none; height: 21px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; width: 21px; }
.app-header__login { background: var(--brand-action); border-radius: 999px; color: var(--on-brand); font-weight: 700; padding: 8px 17px; }
.app-header__login:hover { background: var(--brand-action-hover); color: var(--on-brand); }
.app-header__upload { border: 1px solid var(--border); border-radius: 999px; color: var(--text); font-weight: 700; padding: 7px 14px; }
.app-header__upload:hover { background: var(--surface-2); }
.app-header__profile { color: var(--text); font-weight: 600; gap: 8px; }
.app-header__avatar { align-items: center; background: linear-gradient(135deg, var(--brand-action), var(--brand-action-warm)); border-radius: 50%; color: var(--on-brand); display: inline-flex; font-size: 14px; height: 32px; justify-content: center; width: 32px; }
@media (max-width: 680px) { .app-header { gap: 10px; padding: 0 14px; } .app-header__leading { min-width: auto; } .sidebar-toggle { display: none; } :deep(.brand-logo__wordmark) { display: none; } .app-header__search { width: 100%; } .app-header__upload { display: none; } .app-header__profile > span:last-child { display: none; } }
</style>
