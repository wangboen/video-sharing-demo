# “视界”YouTube 风格前端改版实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 Vue 3 视频分享前端改造成默认深色、支持浅色切换、具有 YouTube 桌面版信息架构和“视界”原创品牌语言的完整中文界面。

**Architecture:** 使用 `App.vue` 作为全局壳层，组合固定顶部导航和可折叠侧边栏；页面负责 API 请求，纯组件负责呈现，`utils/video.js` 负责视频字段兼容、搜索和分类等纯逻辑。主题、侧栏状态通过组合式函数管理并持久化到 `localStorage`，页面间搜索通过 `?q=` 路由参数传递。

**Tech Stack:** Vue 3.4、Vue Router 4.3、Vite 5.2、Axios、Vitest、Vue Test Utils、jsdom、原生 CSS 与内联 SVG。

## Global Constraints

- 全站文案使用中文，品牌名称固定为“视界”。
- 默认深色主题，并支持浅色主题切换和刷新后保持。
- 只对桌面端进行专项设计；窄窗口只保证可访问且布局不崩溃。
- 不修改 Spring Boot 后端和现有 API 协议。
- 不引入点赞、收藏、订阅、评论、服务端搜索、服务端分类或服务端推荐请求。
- 不直接使用 YouTube 名称、商标或品牌资产；图标使用本地内联 SVG。
- 缺失时长、播放量、发布时间或缩略图时使用诚实的降级展示，不伪造数据。
- 当前 `D:\video-test\.git` 为空目录；每个提交步骤仅在工作区恢复为有效 Git 仓库后执行。

---

## 文件结构

### 新建文件

- `video-sharing-frontend/src/components/AppHeader.vue`：顶部品牌、搜索、主题、上传和用户入口。
- `video-sharing-frontend/src/components/AppSidebar.vue`：桌面侧边导航与折叠态。
- `video-sharing-frontend/src/components/BrandLogo.vue`：原创“视界”标志。
- `video-sharing-frontend/src/components/CategoryChips.vue`：分类筛选标签。
- `video-sharing-frontend/src/components/VideoCard.vue`：网格视频卡片。
- `video-sharing-frontend/src/components/VideoListItem.vue`：播放页推荐条目。
- `video-sharing-frontend/src/components/LoadingState.vue`：视频骨架屏。
- `video-sharing-frontend/src/components/EmptyState.vue`：空内容与无筛选结果。
- `video-sharing-frontend/src/components/ErrorState.vue`：错误信息和重试按钮。
- `video-sharing-frontend/src/composables/useTheme.js`：主题初始化、切换和持久化。
- `video-sharing-frontend/src/composables/useSidebar.js`：侧栏状态和持久化。
- `video-sharing-frontend/src/utils/video.js`：资源 URL、字段格式化、搜索和分类。
- `video-sharing-frontend/src/test/setup.js`：测试环境清理。
- `video-sharing-frontend/src/utils/video.test.js`：视频工具函数测试。
- `video-sharing-frontend/src/composables/useTheme.test.js`：主题持久化测试。
- `video-sharing-frontend/src/components/CategoryChips.test.js`：分类交互测试。
- `video-sharing-frontend/src/components/VideoCard.test.js`：卡片降级和导航测试。

### 修改文件

- `video-sharing-frontend/package.json`：增加 Vitest 测试脚本和依赖。
- `video-sharing-frontend/vite.config.js`：配置 jsdom 测试环境。
- `video-sharing-frontend/index.html`：设置中文、主题初始化和页面标题。
- `video-sharing-frontend/src/assets/main.css`：替换 Vue 默认样式为全局设计令牌和布局基础。
- `video-sharing-frontend/src/App.vue`：接入全局壳层。
- `video-sharing-frontend/src/views/HomeView.vue`：首页内容流。
- `video-sharing-frontend/src/views/VideoList.vue`：全部视频、搜索和分类。
- `video-sharing-frontend/src/views/VideoPlayer.vue`：双栏播放页。
- `video-sharing-frontend/src/views/VideoUpload.vue`：创作者工作室式上传页。
- `video-sharing-frontend/src/views/UserDashboard.vue`：频道主页式用户中心。
- `video-sharing-frontend/src/views/LoginView.vue`：中文品牌登录页。
- `video-sharing-frontend/src/views/RegisterView.vue`：中文品牌注册页。
- `video-sharing-frontend/src/views/AboutView.vue`：品牌说明页。

---

### Task 1: 测试基础、主题和视频纯逻辑

**Files:**
- Modify: `video-sharing-frontend/package.json`
- Modify: `video-sharing-frontend/vite.config.js`
- Create: `video-sharing-frontend/src/test/setup.js`
- Create: `video-sharing-frontend/src/composables/useTheme.js`
- Create: `video-sharing-frontend/src/composables/useTheme.test.js`
- Create: `video-sharing-frontend/src/utils/video.js`
- Create: `video-sharing-frontend/src/utils/video.test.js`

**Interfaces:**
- Produces: `useTheme(): { theme, toggleTheme }`，其中 `theme` 为 Vue `Ref<'dark'|'light'>`。
- Produces: `getThumbnailUrl(video): string`、`getVideoUrl(video): string`、`formatViews(value): string`、`formatDate(value): string`、`matchesSearch(video, query): boolean`、`matchesCategory(video, category): boolean`。
- Uses storage keys: `vision-theme`。

- [ ] **Step 1: 安装并配置测试依赖**

Run:

```powershell
cd D:\video-test\video-sharing-frontend
npm install --save-dev vitest @vue/test-utils jsdom
```

在 `package.json` 的 `scripts` 中加入：

```json
"test": "vitest run",
"test:watch": "vitest"
```

在 `vite.config.js` 的 `defineConfig` 中加入：

```js
test: {
  environment: 'jsdom',
  setupFiles: ['./src/test/setup.js'],
},
```

`src/test/setup.js` 内容：

```js
import { afterEach } from 'vitest'

afterEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})
```

- [ ] **Step 2: 先写主题和视频工具失败测试**

`useTheme.test.js` 必须覆盖默认深色、读取浅色缓存和切换持久化；`video.test.js` 必须包含：

```js
expect(formatViews(12800)).toBe('1.2万次观看')
expect(formatViews(undefined)).toBe('暂无播放数据')
expect(matchesSearch({ title: '城市漫步', description: '', user: { username: '小林' } }, '小林')).toBe(true)
expect(matchesCategory({ title: '游戏实况', description: '' }, '游戏')).toBe(true)
expect(getThumbnailUrl({ thumbnailPath: null })).toBe('')
```

- [ ] **Step 3: 运行测试并确认失败**

Run: `npm test -- src/composables/useTheme.test.js src/utils/video.test.js`

Expected: FAIL，原因是两个实现模块尚不存在。

- [ ] **Step 4: 实现最小主题和视频工具**

`useTheme.js` 使用模块级 `ref`，初始化值来自 `localStorage.getItem('vision-theme')`，除明确的 `light` 外均为 `dark`；每次切换同步设置 `document.documentElement.dataset.theme` 与缓存。

`video.js` 使用 `http://localhost` 作为静态资源根路径；搜索拼接 `title`、`description` 和 `user.username` 后转为小写匹配；分类“全部”始终返回 `true`，其他分类按分类词映射匹配标题和简介。分类映射固定为：

```js
export const VIDEO_CATEGORIES = {
  全部: [],
  音乐: ['音乐', '歌曲', '演唱', 'music'],
  游戏: ['游戏', '实况', '电竞', 'game'],
  生活: ['生活', '日常', '记录', 'vlog'],
  科技: ['科技', '数码', '编程', '技术'],
  学习: ['学习', '教程', '课程', '知识'],
}
```

- [ ] **Step 5: 运行测试并确认通过**

Run: `npm test -- src/composables/useTheme.test.js src/utils/video.test.js`

Expected: 两个测试文件全部 PASS。

- [ ] **Step 6: 提交**

```powershell
git add video-sharing-frontend/package.json video-sharing-frontend/package-lock.json video-sharing-frontend/vite.config.js video-sharing-frontend/src/test video-sharing-frontend/src/composables video-sharing-frontend/src/utils
git commit -m "test: add frontend state and video utilities"
```

---

### Task 2: 全局设计系统与应用壳层

**Files:**
- Modify: `video-sharing-frontend/index.html`
- Modify: `video-sharing-frontend/src/assets/main.css`
- Modify: `video-sharing-frontend/src/App.vue`
- Create: `video-sharing-frontend/src/composables/useSidebar.js`
- Create: `video-sharing-frontend/src/components/BrandLogo.vue`
- Create: `video-sharing-frontend/src/components/AppHeader.vue`
- Create: `video-sharing-frontend/src/components/AppSidebar.vue`

**Interfaces:**
- Consumes: `useTheme()` from Task 1。
- Produces: `useSidebar(): { isCollapsed, toggleSidebar }`，storage key 为 `vision-sidebar-collapsed`。
- `AppHeader` props: `user: Object|null`, `sidebarCollapsed: boolean`; emits: `toggle-sidebar`。
- `AppSidebar` props: `user: Object|null`, `collapsed: boolean`。

- [ ] **Step 1: 写壳层组件失败测试**

创建 `src/components/AppShell.test.js`，挂载 `AppHeader` 和 `AppSidebar`，断言：品牌文字为“视界”；菜单按钮触发 `toggle-sidebar`；未登录时显示“登录”；折叠侧栏仍保留带 `aria-label` 的路由入口。

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/components/AppShell.test.js`

Expected: FAIL，原因是壳层组件不存在。

- [ ] **Step 3: 建立设计令牌和首屏主题初始化**

将 `main.css` 完整替换为基于 `[data-theme='dark']` 与 `[data-theme='light']` 的令牌，至少定义：

```css
:root {
  --header-height: 68px;
  --sidebar-width: 232px;
  --sidebar-collapsed-width: 84px;
  --radius-sm: 10px;
  --radius-md: 16px;
  --brand: #ff304f;
  --brand-warm: #ff6b35;
}

:root, [data-theme='dark'] {
  --bg: #0c0c0f;
  --surface: #16161b;
  --surface-2: #222229;
  --text: #f7f7f8;
  --muted: #a6a6af;
  --border: rgba(255, 255, 255, 0.09);
}

[data-theme='light'] {
  --bg: #faf9f7;
  --surface: #ffffff;
  --surface-2: #efeeeb;
  --text: #17171a;
  --muted: #68686f;
  --border: rgba(20, 20, 25, 0.1);
}
```

同时设置全局 reset、按钮焦点、滚动条、页面容器、表单和 `.sr-only`。`index.html` 将 `lang` 改为 `zh-CN`、标题改为“视界 · 发现每一种热爱”，并在加载应用脚本前读取 `vision-theme` 设置 `data-theme`。

- [ ] **Step 4: 实现壳层组件与 App 组合**

`BrandLogo` 使用内联 SVG 红色圆角播放图形；`AppHeader` 在非内容流页面提交搜索时执行：

```js
router.push({ name: 'videos', query: query.value.trim() ? { q: query.value.trim() } : {} })
```

`App.vue` 保留现有用户状态刷新逻辑，并改为：

```vue
<AppHeader :user="user" :sidebar-collapsed="isCollapsed" @toggle-sidebar="toggleSidebar" />
<AppSidebar :user="user" :collapsed="isCollapsed" />
<main class="app-main" :class="{ 'app-main--collapsed': isCollapsed }">
  <RouterView />
</main>
```

- [ ] **Step 5: 运行壳层测试和构建**

Run: `npm test -- src/components/AppShell.test.js`

Expected: PASS。

Run: `npm run build`

Expected: Vite 构建成功，无 Vue 模板错误。

- [ ] **Step 6: 提交**

```powershell
git add video-sharing-frontend/index.html video-sharing-frontend/src/assets/main.css video-sharing-frontend/src/App.vue video-sharing-frontend/src/components video-sharing-frontend/src/composables/useSidebar.js
git commit -m "feat: build Vision app shell and themes"
```

---

### Task 3: 视频内容流组件与首页/列表页

**Files:**
- Create: `video-sharing-frontend/src/components/CategoryChips.vue`
- Create: `video-sharing-frontend/src/components/CategoryChips.test.js`
- Create: `video-sharing-frontend/src/components/VideoCard.vue`
- Create: `video-sharing-frontend/src/components/VideoCard.test.js`
- Create: `video-sharing-frontend/src/components/LoadingState.vue`
- Create: `video-sharing-frontend/src/components/EmptyState.vue`
- Create: `video-sharing-frontend/src/components/ErrorState.vue`
- Modify: `video-sharing-frontend/src/views/HomeView.vue`
- Modify: `video-sharing-frontend/src/views/VideoList.vue`

**Interfaces:**
- Consumes: `VIDEO_CATEGORIES`、格式化函数和匹配函数 from Task 1。
- `CategoryChips` props: `modelValue: string`; emits: `update:modelValue`。
- `VideoCard` props: `video: Object`; navigation target: `/video/${video.id}`。
- `ErrorState` props: `message: string`; emits: `retry`。

- [ ] **Step 1: 写组件失败测试**

`CategoryChips.test.js` 点击“游戏”后断言发出 `update:modelValue` 且值为“游戏”。`VideoCard.test.js` 用缺失缩略图、时间和播放量的数据挂载，断言出现本地品牌占位区域、“暂无播放数据”，且标题链接指向 `/video/7`。

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/components/CategoryChips.test.js src/components/VideoCard.test.js`

Expected: FAIL，原因是内容流组件不存在。

- [ ] **Step 3: 实现内容流组件**

`VideoCard` 使用语义化链接包裹缩略图和标题；图片 `@error` 后隐藏图片并显示品牌占位；作者头像使用用户名首字。`LoadingState` 接受 `count`，默认渲染 8 个 16:9 骨架；`EmptyState` 接受 `title`、`description` 和可选操作链接；`ErrorState` 的重试按钮必须是原生 `button`。

- [ ] **Step 4: 实现页面数据流**

首页和视频列表均使用以下请求状态：

```js
const videos = ref([])
const loading = ref(true)
const error = ref('')
const selectedCategory = ref('全部')
```

`VideoList.vue` 从 `route.query.q` 读取搜索词，并通过 `computed` 同时应用 `matchesSearch` 和 `matchesCategory`。首页展示标题“为你推荐”和说明文字，视频列表展示“全部视频”。两页均提供重试函数，失败文案为“视频加载失败，请检查服务是否正常运行。”

- [ ] **Step 5: 运行测试和构建**

Run: `npm test -- src/components/CategoryChips.test.js src/components/VideoCard.test.js`

Expected: PASS。

Run: `npm run build`

Expected: 构建成功，首页不再显示 `Home Page`。

- [ ] **Step 6: 提交**

```powershell
git add video-sharing-frontend/src/components video-sharing-frontend/src/views/HomeView.vue video-sharing-frontend/src/views/VideoList.vue
git commit -m "feat: add branded video discovery feed"
```

---

### Task 4: 双栏视频播放页

**Files:**
- Create: `video-sharing-frontend/src/components/VideoListItem.vue`
- Create: `video-sharing-frontend/src/components/VideoListItem.test.js`
- Modify: `video-sharing-frontend/src/views/VideoPlayer.vue`

**Interfaces:**
- Consumes: `getThumbnailUrl`、`getVideoUrl`、`formatViews`、`formatDate`。
- `VideoListItem` props: `video: Object`；导航目标 `/video/${video.id}`。
- 播放页本地交互状态: `liked: Ref<boolean>`、`saved: Ref<boolean>`。

- [ ] **Step 1: 写推荐条目失败测试**

用 id 为 9、标题为“夜晚城市”的视频挂载 `VideoListItem`，断言缩略图链接、标题、作者和 `/video/9` 路径存在；缺失缩略图时断言显示品牌占位。

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/components/VideoListItem.test.js`

Expected: FAIL，原因是组件不存在。

- [ ] **Step 3: 实现推荐组件和播放页请求**

播放页在 `watch(() => route.params.id, loadPage, { immediate: true })` 中并行请求当前视频与列表，推荐列表使用：

```js
recommendations.value = listResponse.data
  .filter((item) => String(item.id) !== String(route.params.id))
  .slice(0, 12)
```

当前视频失败显示整页 `ErrorState`；推荐失败只在右栏显示局部提示。播放器使用 `:src="getVideoUrl(video)"` 和 `:poster="getThumbnailUrl(video)"`。

- [ ] **Step 4: 实现页面视觉与诚实交互**

左栏包含标题、播放量与日期、点赞和稍后观看按钮、作者首字头像、用户名及简介面板；按钮只切换 `aria-pressed` 和本地样式，不调用 API。右栏标题为“接着看”，使用 `VideoListItem`。

- [ ] **Step 5: 运行测试和构建**

Run: `npm test -- src/components/VideoListItem.test.js`

Expected: PASS。

Run: `npm run build`

Expected: 构建成功。

- [ ] **Step 6: 提交**

```powershell
git add video-sharing-frontend/src/components/VideoListItem.vue video-sharing-frontend/src/components/VideoListItem.test.js video-sharing-frontend/src/views/VideoPlayer.vue
git commit -m "feat: redesign video watch experience"
```

---

### Task 5: 上传、用户中心和认证页面

**Files:**
- Modify: `video-sharing-frontend/src/views/VideoUpload.vue`
- Modify: `video-sharing-frontend/src/views/UserDashboard.vue`
- Modify: `video-sharing-frontend/src/views/LoginView.vue`
- Modify: `video-sharing-frontend/src/views/RegisterView.vue`
- Create: `video-sharing-frontend/src/views/AuthViews.test.js`
- Create: `video-sharing-frontend/src/views/VideoUpload.test.js`

**Interfaces:**
- 保留现有 `api.post('/api/videos/upload', formData)`、`api.post('/api/auth/login', payload)` 和 `api.post('/api/auth/register', payload)`。
- 保留 `saveAuth`、`clearAuth`、`getStoredUser` 的签名。

- [ ] **Step 1: 写表单兼容失败测试**

`AuthViews.test.js` 断言登录页有“用户名”“密码”“登录”，注册页有“用户名”“邮箱”“密码”“创建账号”。`VideoUpload.test.js` 模拟文件并提交，断言 FormData 包含 `title`、`description`、`file`，且存在缩略图时包含 `thumbnail`。

- [ ] **Step 2: 运行测试并确认当前界面失败**

Run: `npm test -- src/views/AuthViews.test.js src/views/VideoUpload.test.js`

Expected: FAIL，原因是当前文案为英文且页面尚无新结构。

- [ ] **Step 3: 重构上传页**

保留现有文件选择、预览 URL 回收和请求逻辑，增加 `uploading` 状态防止重复提交。使用双栏工作室布局，中文文案为“发布新视频”“选择视频文件”“上传封面”“视频标题”“视频简介”“立即发布”；成功文案为“视频发布成功”，并提供 `/videos` 链接。

- [ ] **Step 4: 重构认证页和用户中心**

登录注册页共享相同 CSS 类结构：`.auth-page`、`.auth-brand-panel`、`.auth-form-panel`。不得改变用户名和密码约束或重定向规则。用户中心显示“我的频道”、用户名、邮箱、“发布视频”“浏览视频”和“退出登录”。

- [ ] **Step 5: 运行测试和构建**

Run: `npm test -- src/views/AuthViews.test.js src/views/VideoUpload.test.js`

Expected: PASS。

Run: `npm run build`

Expected: 构建成功。

- [ ] **Step 6: 提交**

```powershell
git add video-sharing-frontend/src/views/VideoUpload.vue video-sharing-frontend/src/views/UserDashboard.vue video-sharing-frontend/src/views/LoginView.vue video-sharing-frontend/src/views/RegisterView.vue video-sharing-frontend/src/views/AuthViews.test.js video-sharing-frontend/src/views/VideoUpload.test.js
git commit -m "feat: redesign creator and account pages"
```

---

### Task 6: 关于页、全量验证和桌面视觉检查

**Files:**
- Modify: `video-sharing-frontend/src/views/AboutView.vue`
- Modify: `video-sharing-frontend/src/router/index.js` only if route metadata is needed for page titles

**Interfaces:**
- 不新增 API。
- 可选路由元数据接口: `meta.title: string`，由 `router.afterEach` 更新 `document.title`。

- [ ] **Step 1: 写路由和中文文案测试**

创建 `src/router/router.test.js`，断言八个既有路径仍存在、受保护路由仍有 `requiresAuth`，并断言关于页包含“关于视界”“发现每一种热爱”。

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/router/router.test.js`

Expected: FAIL，原因是关于页仍是默认英文内容。

- [ ] **Step 3: 完成关于页和页面标题**

关于页使用三段能力卡片：“发现内容”“分享创作”“专注观看”，并说明这是一个视频分享体验。为每个路由加入中文 `meta.title`，在 `router.afterEach` 设置：

```js
document.title = `${to.meta.title || '发现'} · 视界`
```

- [ ] **Step 4: 运行完整自动化验证**

Run: `npm test`

Expected: 所有测试文件 PASS，无未处理 Promise rejection。

Run: `npm run build`

Expected: `dist` 生产构建成功，无编译错误。

- [ ] **Step 5: 启动前端并执行桌面视觉检查**

Run: `npm run dev -- --host 127.0.0.1`

在 1440×900 桌面视口检查：

- 默认深色首屏无明显浅色闪烁。
- 顶栏固定且搜索框居中，展开和收起侧栏时内容不被遮挡。
- 首页与视频列表为四列 16:9 卡片，长标题不会破坏网格。
- 浅色主题所有正文、边框和按钮清晰可辨。
- 播放页左右栏比例合理，推荐条目不溢出。
- 登录、注册、上传和用户中心均为中文且使用同一品牌视觉。
- 键盘 Tab 能看到清晰焦点，图标按钮具有中文可访问名称。

- [ ] **Step 6: 最终提交**

```powershell
git add video-sharing-frontend/src/views/AboutView.vue video-sharing-frontend/src/router/index.js video-sharing-frontend/src/router/router.test.js
git commit -m "feat: complete Vision frontend redesign"
```

---

## 完成标准

- 八个既有路由均可访问，鉴权边界保持不变。
- 深色主题为默认值，浅色主题与侧栏状态刷新后保持。
- 首页、列表页、播放页、上传页、用户中心、登录、注册和关于页全部使用中文原创“视界”视觉。
- 搜索、分类、侧栏和主题切换均可操作且不依赖新增后端接口。
- 不向服务器发送后端未支持的互动或分类请求。
- `npm test` 和 `npm run build` 均成功。
- 1440×900 桌面视觉检查无明显遮挡、溢出、低对比度或主题闪烁。
