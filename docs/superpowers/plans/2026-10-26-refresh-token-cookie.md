# Refresh Token Cookie 实施计划

> **执行要求：** 按 `superpowers:executing-plans` 逐项执行。每项使用复选框记录进度。

**目标：** 将浏览器内持久化的 JWT 改为短期内存 access token 和轮换式 HttpOnly refresh Cookie，覆盖普通用户与员工入口。

**架构：** 后端签发有效期 15 分钟的 access JWT，并将有效期 14 天的随机 refresh token 摘要存入 Redis。两个 Vue 前端只在内存保存 access token，通过独立刷新接口恢复会话；刷新和退出请求使用 Cookie 凭据及 CSRF 请求头。

**技术栈：** Spring Boot、Spring Security、Redis、Java、Vue 3、Axios。

**设计说明：** `docs/superpowers/specs/2026-10-26-refresh-token-cookie.md`

## 全局约束

- 普通 API 继续通过 Bearer access JWT 授权。
- access token 和 refresh token 都不得写入 localStorage。
- 用户与员工使用独立的 refresh Cookie 和接口。
- 刷新和退出请求必须校验明确的允许来源，并要求自定义 CSRF 请求头。
- 退出时撤销当前 access session 和 refresh 凭证。

---

### 任务一：后端 refresh token 与 Cookie 生命周期

**文件：**

- 新建 `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/security/RefreshTokenService.java`。
- 新建 `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/security/CsrfRequestValidator.java`。
- 新建 `video-sharing-backend/src/main/java/com/videosharing/videosharingspringboot/config/AuthCookieFactory.java`。
- 修改 `security/JwtService.java`、`security/RedisSessionService.java`、`security/SecurityConfig.java`、`controller/UserController.java`、`controller/StaffAuthController.java` 和 `src/main/resources/application*.properties`。

**接口约定：**

- `RefreshTokenService.issue(userId, accountType, accessSessionId)` 生成 32 字节随机 token；Redis 只保存 SHA-256 摘要、用户 ID、账号类型、access session ID 与 14 天过期时间；原始 token 仅返回一次。
- `RefreshTokenService.consume(rawToken, expectedAccountType)` 原子消费 Redis 记录，拒绝缺失、过期或账号类型不匹配的 token，并撤销旧 access session。
- `RefreshTokenService.revoke(rawToken, expectedAccountType)` 撤销匹配的 refresh 记录及其关联的 access session。
- `POST /api/auth/refresh` 和 `POST /api/staff/auth/refresh` 返回账号资料与新 access token，并设置轮换后的 Cookie。
- 两个对应的 `/logout` 接口撤销会话并清除正确路径下的 Cookie；即使 token 已无效，也返回清除 Cookie 的响应。
- 登录和注册返回 15 分钟 access token，并设置 refresh Cookie。

- [x] 实现随机 token、摘要存储、原子消费与轮换、14 天滚动过期和旧 access session 撤销。
- [x] 修改 `RedisSessionService.createSession`，确保小于一小时的 access token TTL 不会被截断为 0。
- [x] 接入用户登录、注册、刷新、退出，以及员工登录、刷新、退出；刷新与退出校验 Origin 和 `X-CSRF-Guard` 请求头。
- [x] 配置明确来源的 credentialed CORS、允许 `X-CSRF-Guard`，以及 access/refresh 生命周期和 Cookie 属性。
- [x] 检查后端编译结果，不运行测试套件。

### 任务二：用户端内存 access token 与会话恢复

**文件：**

- 修改 `video-sharing-frontend/src/services/auth.js`、`api.js`、`main.js`、`App.vue`、`router/index.js`、`views/LoginView.vue`、`views/RegisterView.vue` 和 `views/UserDashboard.vue`。

**接口约定：**

- 认证服务提供内存态 access token 的读取、写入、保存资料和清理行为。
- Axios 自动携带 Cookie 与当前 Bearer token；并发刷新共享同一请求；每个失败请求最多重试一次；登录、注册、刷新和退出不触发递归刷新。

- [x] 将用户 access token 改为内存状态，只在 localStorage 保留用户资料，并删除旧 `authToken`。
- [x] 增加 credentialed refresh、并发刷新合并和单次请求重试逻辑。
- [x] 在挂载受保护路由前刷新会话并请求 `/api/auth/me`；刷新无效时清除本地资料。
- [x] 更新登录、注册与退出流程；刷新和退出请求发送 CSRF 请求头。
- [x] 检查用户端构建结果，不运行测试套件。

### 任务三：员工端内存 access token 与会话恢复

**文件：**

- 修改 `video-sharing-staff-frontend/src/services/staffAuth.js`、`api.js`、`main.js`、`App.vue`、`router/index.js`、`views/StaffLoginView.vue` 和 `components/StaffShell.vue`。

**接口约定：**

- 员工认证服务仅在 localStorage 保存员工资料，并在内存保留 access token。
- 员工刷新和退出使用 `/api/staff/auth/*` 及员工专属 refresh Cookie；刷新响应包含当前员工资料。

- [x] 将员工 access token 改为内存状态，保留员工资料并删除旧 `staffAuthToken`。
- [x] 增加 credentialed refresh、并发刷新合并和单次重试；认证接口不触发递归刷新。
- [x] 在路由守卫运行前恢复员工会话，并保持用户和员工资料与 Cookie 相互隔离。
- [x] 更新员工登录和退出流程，退出路径改为 `/api/staff/auth/logout`，刷新和退出请求发送 CSRF 请求头。
- [x] 检查员工端构建结果，不运行测试套件。

### 任务四：集成检查

- [x] 确认开发与生产来源配置符合 credentialed CORS 要求。
- [x] 检查代码中不再持久化或记录 access/refresh 凭证，也不存在认证接口递归刷新。
- [x] 构建后端和两个前端，并报告构建无法覆盖的场景。
