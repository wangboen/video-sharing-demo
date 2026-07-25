# 视频分享系统部署文档

本文档说明如何部署当前项目的三个主要组件：

- `video-sharing-backend`：Spring Boot 后端，提供用户认证、视频上传、公开视频列表、审核和管理员接口。
- `video-sharing-frontend`：普通用户前端，部署在站点根路径 `/`。
- `video-sharing-staff-frontend`：审核/管理员前端，部署在 `/staff/`。

生产推荐使用同源部署：公网只暴露 HTTPS，TLS 终止层转发到本机 Nginx，Nginx 再代理 `/api/` 到 Spring Boot，并直接托管前端构建产物和公开视频静态资源。

TLS terminator or load balancer is mandatory. The Nginx listener on `127.0.0.1:8081` and the Spring Boot backend on `127.0.0.1:8080` must not be publicly exposed.

## 一、运行环境

### 基础依赖

- JDK 17
- Maven 3.9+
- Node.js 20.17+，npm 11.12.x 推荐用于审核端前端
- MySQL 8+
- Redis 6+
- Nginx
- HTTPS 证书和 TLS 终止层，例如云负载均衡、Caddy、Nginx TLS server 或其他网关

### 默认端口

| 服务 | 默认地址 | 说明 |
| --- | --- | --- |
| Spring Boot | `127.0.0.1:8080` | 后端 API |
| Nginx | `127.0.0.1:8081` | 本机明文入口，只给 TLS 终止层访问 |
| Redis | `127.0.0.1:6379` | JWT 会话存储 |
| MySQL | `127.0.0.1:3306` | 业务数据库 |

生产环境不要直接暴露 Spring Boot、Redis、MySQL 或 Nginx 的 `8081` 明文端口。

## 二、生产目录规划

推荐目录如下：

```text
/srv/video-sharing/
  user/dist/                    # 普通用户前端构建产物
  staff/dist/                   # 审核端前端构建产物
  public/uploads/               # 已通过审核的公开视频文件
  public/thumbnails/            # 已通过审核的公开缩略图
  private/pending/uploads/      # 待审核视频文件，不允许被 Nginx 暴露
  private/pending/thumbnails/   # 待审核缩略图，不允许被 Nginx 暴露
```

创建目录并设置运行用户权限：

```bash
sudo mkdir -p /srv/video-sharing/user/dist
sudo mkdir -p /srv/video-sharing/staff/dist
sudo mkdir -p /srv/video-sharing/public/uploads
sudo mkdir -p /srv/video-sharing/public/thumbnails
sudo mkdir -p /srv/video-sharing/private/pending/uploads
sudo mkdir -p /srv/video-sharing/private/pending/thumbnails
sudo chown -R app:app /srv/video-sharing
```

其中 `app` 是示例服务账号，请替换为你的实际运行用户。

## 三、后端配置

后端配置文件位于 `video-sharing-backend/src/main/resources/application.properties`。

生产环境必须通过环境变量覆盖敏感配置，不要使用仓库中的本地默认值。

### 必填或强烈建议配置

| 环境变量 | 示例 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://db.example.com:3306/video_sharing?useSSL=true&serverTimezone=UTC` | 生产数据库 JDBC URL |
| `DB_USERNAME` | `video_app` | 最小权限数据库账号 |
| `DB_PASSWORD` | `********` | 数据库密码 |
| `JWT_SECRET` | `openssl rand -base64 32` 生成值 | JWT 签名密钥，必须足够长且唯一 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | `********` | Redis 密码；本地无密码可留空 |
| `REDIS_DATABASE` | `0` | Redis database |
| `FILE_UPLOAD_DIR` | `/srv/video-sharing/public/uploads` | 公开视频目录 |
| `FILE_THUMBNAIL_DIR` | `/srv/video-sharing/public/thumbnails` | 公开缩略图目录 |
| `PENDING_UPLOAD_DIR` | `/srv/video-sharing/private/pending/uploads` | 待审核视频目录 |
| `PENDING_THUMBNAIL_DIR` | `/srv/video-sharing/private/pending/thumbnails` | 待审核缩略图目录 |
| `CORS_ALLOWED_ORIGINS` | `https://your-domain.example` | 跨域白名单；同源部署可保持严格配置 |
| `MODERATION_LEASE_DURATION_MINUTES` | `15` | 审核任务租约时间，必须大于 2 分钟 |

### 管理员初始化

首次部署可以设置以下变量创建初始管理员：

| 环境变量 | 说明 |
| --- | --- |
| `ADMIN_USERNAME` | 初始管理员用户名，长度 3 到 50 |
| `ADMIN_EMAIL` | 初始管理员邮箱 |
| `ADMIN_PASSWORD` | 初始管理员密码，长度 8 到 72 |

三者必须同时设置，或同时不设置。管理员创建后再次启动不会覆盖已有管理员密码。

### JWT 与 Redis 会话

系统使用 JWT 作为浏览器持有的身份凭证，同时在 Redis 中保存活动会话：

- JWT 中包含随机 `jti`。
- Redis 使用 `auth:session:{jti}` 保存用户 ID。
- Redis TTL 与 JWT 有效期一致，当前为 2 小时。
- 每次访问受保护接口时，后端会同时校验 JWT 签名、JWT 过期时间和 Redis 会话。
- 退出登录会删除当前 `jti` 对应的 Redis 会话，使当前 token 立即失效。
- Redis 不可用时，受保护接口会按未认证处理。

## 四、数据库准备

创建数据库和低权限账号：

```sql
CREATE DATABASE video_sharing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'video_app'@'%' IDENTIFIED BY 'replace-with-strong-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON video_sharing.* TO 'video_app'@'%';
FLUSH PRIVILEGES;
```

后端当前使用 `spring.jpa.hibernate.ddl-auto=update`，启动时会自动补齐表结构，并执行旧数据迁移：

- 旧用户没有角色时迁移为 `USER`。
- 旧视频没有状态时迁移为 `PENDING`。
- 待审核媒体会被迁移到私有 pending 目录。
- 乐观锁版本字段为空时回填为 `0`。

生产环境首次上线前建议先备份数据库和媒体目录。

## 五、构建前端

同源生产部署时，两个前端的 `VITE_API_BASE_URL` 可以不设置，构建后会使用相对路径请求 `/api/`。

普通用户前端：

```bash
cd video-sharing-frontend
npm ci
npm run build
sudo rsync -a --delete dist/ /srv/video-sharing/user/dist/
```

审核端前端：

```bash
cd video-sharing-staff-frontend
npm ci
npm run build
sudo rsync -a --delete dist/ /srv/video-sharing/staff/dist/
```

开发环境如果前端和后端不同源，可以在 `.env` 中设置：

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## 六、构建并启动后端

构建：

```bash
cd video-sharing-backend
mvn clean package
```

启动示例：

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/video_sharing?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USERNAME='video_app'
export DB_PASSWORD='replace-with-strong-password'
export JWT_SECRET='replace-with-a-base64-32-byte-or-longer-secret'
export REDIS_HOST='127.0.0.1'
export REDIS_PORT='6379'
export REDIS_PASSWORD=''
export REDIS_DATABASE='0'
export FILE_UPLOAD_DIR='/srv/video-sharing/public/uploads'
export FILE_THUMBNAIL_DIR='/srv/video-sharing/public/thumbnails'
export PENDING_UPLOAD_DIR='/srv/video-sharing/private/pending/uploads'
export PENDING_THUMBNAIL_DIR='/srv/video-sharing/private/pending/thumbnails'
export CORS_ALLOWED_ORIGINS='https://your-domain.example'
export MODERATION_LEASE_DURATION_MINUTES='15'

java -jar target/videosharingspringboot-0.0.1-SNAPSHOT.jar
```

Media path values without shell quoting:

```text
FILE_UPLOAD_DIR=/srv/video-sharing/public/uploads
FILE_THUMBNAIL_DIR=/srv/video-sharing/public/thumbnails
PENDING_UPLOAD_DIR=/srv/video-sharing/private/pending/uploads
PENDING_THUMBNAIL_DIR=/srv/video-sharing/private/pending/thumbnails
```

本地开发可以使用 dev profile：

```bash
cd video-sharing-backend
$env:SPRING_PROFILES_ACTIVE='dev'
mvn spring-boot:run
```

Equivalent environment value: `SPRING_PROFILES_ACTIVE=dev`.

`dev` profile 只用于本地开发，不能用于生产。

## 七、配置 Nginx

项目提供的参考配置在：

```text
video-sharing-backend/src/main/resources/nginx.conf
```

该配置做了这些事：

- 监听 `127.0.0.1:8081`。
- `/` 托管普通用户前端。
- `/staff/` 托管审核端前端。
- `/api/` 代理到 `127.0.0.1:8080`。
- `/uploads/` 和 `/thumbnails/` 直接返回公开视频与缩略图。
- `/pending/` 永远返回 404，避免待审核文件被公开访问。
- `client_max_body_size 545m` 与 Spring Boot 上传限制保持一致。

安装配置示例：

```bash
sudo cp video-sharing-backend/src/main/resources/nginx.conf /etc/nginx/conf.d/video-sharing.conf
sudo nginx -t
sudo systemctl reload nginx
```

如果 TLS 也由 Nginx 负责，可以额外增加一个公网 `443` server，将 HTTPS 请求转发到 `127.0.0.1:8081`，并保留 `Host` 与 `X-Forwarded-Proto: https`。

## 八、启动顺序

建议按以下顺序启动或发布：

1. MySQL
2. Redis
3. Spring Boot 后端
4. Nginx
5. TLS 终止层或公网负载均衡

后端启动时会检查和迁移部分历史数据。若待审核媒体目录状态异常，启动可能失败，这是为了避免把未审核媒体错误暴露到公开目录。

## 九、接口访问策略

当前公开接口和资源：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/staff/auth/login`
- `GET /api/videos`
- `/uploads/**`
- `/thumbnails/**`

当前受保护接口：

- `GET /api/auth/me`
- `POST /api/auth/logout`
- `POST /api/videos/upload`
- `/api/moderation/**`
- `/api/admin/**`
- 用户个人中心相关接口

注意：后端已经删除单个视频访问接口 `GET /api/videos/{id}`。播放页通过 `GET /api/videos` 获取公开列表后在前端匹配当前视频 ID。

## 十、部署验收

### 后端验收

```bash
cd video-sharing-backend
mvn test
```

检查：

- 注册成功返回 `token`。
- 登录成功返回 `token`，登录失败返回 401。
- `GET /api/auth/me` 携带有效 Bearer Token 返回当前用户。
- 退出登录后，同一 token 再访问上传接口返回 401。
- `GET /api/videos` 不需要 token。
- `POST /api/videos/upload` 缺少 token 时返回 401。

### 前端验收

```bash
cd video-sharing-frontend
npm test
npm run build

cd ../video-sharing-staff-frontend
npm test
npm run build
```

浏览器检查：

- 访问 `/` 可以进入普通用户前端。
- 访问 `/staff/` 可以进入审核端前端。
- 登录后刷新页面仍保持会话。
- 退出登录会调用 `/api/auth/logout` 并清理本地登录态。
- 未登录访问上传页或个人中心会跳转登录页。
- 上传视频时只提交标题、描述、视频和缩略图，用户身份由 JWT 决定。
- 公开视频和缩略图通过 Nginx 的 `/uploads/`、`/thumbnails/` 加载。

### Nginx 与静态资源检查

```bash
curl -I https://your-domain.example/
curl -I https://your-domain.example/staff/
curl -I https://your-domain.example/api/videos
curl -I https://your-domain.example/pending/
```

预期：

- `/` 返回普通用户前端。
- `/staff/` 返回审核端前端。
- `/api/videos` 返回 200。
- `/pending/` 返回 404。

## 十一、常见问题

### 1. 登录成功后访问受保护接口仍然 401

检查 Redis 是否可访问，以及 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` 是否与后端运行环境一致。JWT 签名正确但 Redis 会话缺失时，系统会按未认证处理。

### 2. 前端请求到了错误的 API 地址

生产同源部署通常不要设置 `VITE_API_BASE_URL`，让前端请求相对路径 `/api/`。如果构建时设置了绝对地址，修改 `.env` 后必须重新执行 `npm run build`。

### 3. 上传视频返回 413

检查 Nginx `client_max_body_size` 是否为 `545m`，并确认 Spring Boot：

```properties
spring.servlet.multipart.max-file-size=512MB
spring.servlet.multipart.max-request-size=545MB
```

### 4. 视频或缩略图 404

确认后端环境变量和 Nginx alias 指向同一组公开目录：

```text
FILE_UPLOAD_DIR=/srv/video-sharing/public/uploads
FILE_THUMBNAIL_DIR=/srv/video-sharing/public/thumbnails
```

同时确认待审核目录没有被 Nginx 暴露。

### 5. 初始管理员无法创建

检查 `ADMIN_USERNAME`、`ADMIN_EMAIL`、`ADMIN_PASSWORD` 是否三个都设置，且没有与已有审核账号冲突。创建后再次启动不会覆盖已有管理员。
