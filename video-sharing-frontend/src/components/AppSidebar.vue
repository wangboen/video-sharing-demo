<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  user: { type: Object, default: null },
  collapsed: { type: Boolean, default: false },
})
</script>

<template>
  <aside class="app-sidebar" :class="{ 'app-sidebar--collapsed': collapsed }">
    <nav class="app-sidebar__nav" aria-label="主导航">
      <RouterLink class="app-sidebar__link" to="/" aria-label="首页">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1V10Z" /></svg><span>首页</span>
      </RouterLink>
      <RouterLink class="app-sidebar__link" to="/videos" aria-label="发现视频">
        <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3" /><path d="m10 9 5 3-5 3V9Z" /></svg><span>发现</span>
      </RouterLink>
      <RouterLink class="app-sidebar__link" to="/about" aria-label="关于视界">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8" /><path d="M12 11v5M12 8h.01" /></svg><span>关于</span>
      </RouterLink>

      <div class="app-sidebar__divider" />

      <RouterLink v-if="user" class="app-sidebar__link" to="/dashboard" aria-label="个人中心">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3" /><path d="M5 20c.5-4 2.8-6 7-6s6.5 2 7 6" /></svg><span>个人中心</span>
      </RouterLink>
      <RouterLink v-if="user" class="app-sidebar__link" to="/upload" aria-label="发布视频">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg><span>发布视频</span>
      </RouterLink>
      <RouterLink v-else class="app-sidebar__link" to="/login" aria-label="登录">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h7a2 2 0 0 0 2-2v-3M10 12h11M18 8l4 4-4 4" /></svg><span>登录</span>
      </RouterLink>
      <RouterLink v-if="!user" class="app-sidebar__link" to="/register" aria-label="注册">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3" /><path d="M3 20c.5-4 2.5-6 6-6 2.3 0 4 .8 5 2.3M18 9v8M14 13h8" /></svg><span>注册</span>
      </RouterLink>
    </nav>
  </aside>
</template>

<style scoped>
.app-sidebar { background: var(--surface); border-right: 1px solid var(--border); bottom: 0; left: 0; padding: 22px 14px; position: fixed; top: var(--header-height); transition: width 180ms ease; width: var(--sidebar-width); z-index: 10; }
.app-sidebar--collapsed { width: var(--sidebar-collapsed-width); }
.app-sidebar__nav { display: grid; gap: 6px; }
.app-sidebar__link { align-items: center; border-radius: var(--radius-sm); color: var(--muted); display: flex; font-weight: 650; gap: 14px; min-height: 46px; padding: 0 14px; white-space: nowrap; }
.app-sidebar__link:hover, .app-sidebar__link.router-link-exact-active { background: color-mix(in srgb, var(--brand) 12%, transparent); color: var(--text); }
.app-sidebar__link.router-link-exact-active svg { stroke: var(--brand); }
.app-sidebar__link svg { fill: none; flex: 0 0 auto; height: 21px; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; width: 21px; }
.app-sidebar__divider { background: var(--border); height: 1px; margin: 11px 8px; }
.app-sidebar--collapsed .app-sidebar__link { justify-content: center; padding: 0; }
.app-sidebar--collapsed .app-sidebar__link span { clip: rect(0 0 0 0); clip-path: inset(50%); height: 1px; overflow: hidden; position: absolute; white-space: nowrap; width: 1px; }
@media (max-width: 680px) { .app-sidebar { display: none; } }
</style>
