# Refresh Token Cookie 设计

## 目标

让长期凭证不再暴露给 JavaScript，从而降低 XSS 窃取凭证的风险；普通 API 仍使用 Bearer access token。

## 当前状态

普通用户和员工登录都会签发有效期为两小时的 JWT。两个前端都将其持久化到 `localStorage`，后端在 Redis 中跟踪 JWT session ID。目前没有刷新接口。

## 选定方案

签发有效期 15 分钟的 access JWT，以及有效期 14 天的不透明随机 refresh token。access token 仅保存在前端内存并用于 Bearer 请求；refresh token 通过 HttpOnly Cookie 下发。用户端与员工端使用不同的 Cookie 名称和路径。Redis 只保存 refresh token 的 SHA-256 摘要及会话信息。每次刷新都会轮换 refresh token 并撤销旧 access session；退出时撤销会话并清除 Cookie。

页面启动时，如果本地保存了用户资料，前端先调用对应的刷新接口，再加载受保护路由。用户端随后请求 `/api/auth/me`；员工端刷新接口直接返回员工资料。登录和注册会把返回的资料保存在本地，并将 access token 保留在内存。退出时先请求后端，再清除本地资料和内存状态。

Cookie 使用 `HttpOnly`、`SameSite=Lax`，生产环境启用 `Secure`，并限制到各自认证路径。刷新和退出请求必须带自定义 CSRF 请求头，并校验 `Origin`。CORS 仅允许已配置的明确来源携带凭据。Cookie 的 Secure 与 SameSite 属性可按部署环境配置。

## 目标范围

- access token 和 refresh token 都不写入 `localStorage`。
- refresh token 只能使用一次；重放或过期凭证会被拒绝。
- 用户端和员工端会话相互隔离。
- 退出时撤销当前 access session 与 refresh 凭证。
- 普通 API 继续使用 Bearer access JWT 授权。

## 不在本次范围

- 修改账号角色或授权规则。
- 更改部署域名结构。
- 增加 refresh token 重放宽限期或跨设备会话管理。

## 约束与权衡

- 被窃取的 access token 在过期前仍可使用；短有效期用于限制风险窗口。
- HttpOnly 可阻止脚本读取 refresh 凭证，但页面被 XSS 控制期间仍可能被用于发起已认证请求。
- Cookie 刷新和退出接口需要 CSRF 与来源校验。
- 迁移时会清除旧的 localStorage access token，用户需要重新登录。

## 备选方案

- 将现有 JWT 放进 Cookie：改动较少，但长期凭证会随普通 API 请求自动携带，并需要让所有 API 改用 Cookie 认证。
- 短期 access JWT 加 HttpOnly refresh Cookie：本次采用此方案，可缩短 access token 暴露窗口，并保护 refresh 凭证，同时保留普通 API 的现有授权方式。

## 实施约定

沿用现有 Spring Boot、Redis、Axios 和 Vue 模式。用户与员工分别使用独立的 refresh 接口和 Cookie。access token 有效期、refresh token 有效期、Cookie Secure、SameSite 通过应用配置管理。
