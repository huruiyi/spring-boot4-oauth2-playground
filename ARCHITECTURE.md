# Spring-Boot4-OAuth2-Playground 架构文档

## 目录

- [1. 项目模块结构](#1-项目模块结构)
- [2. auth-server 代码结构](#2-auth-server-代码结构)
- [3. resource-server 代码结构](#3-resource-server-代码结构)
- [4. oauth2-client 代码结构](#4-oauth2-client-代码结构)
- [5. oauth2-client-demo 代码结构](#5-oauth2-client-demo-代码结构)
- [6. 数据库](#6-数据库)
- [7. OAuth2 客户端注册](#7-oauth2-客户端注册)
- [8. 安全配置](#8-安全配置)
- [9. JWT 自定义 Claims](#9-jwt-自定义-claims)
- [10. 账户锁定与 IP 安全](#10-账户锁定与-ip-安全)
- [11. MFA (TOTP) 两步验证](#11-mfa-totp-两步验证)
- [12. Token 管理](#12-token-管理)
- [13. SPA 客户端](#13-spa-客户端)
- [14. 模板文件](#14-模板文件)
- [15. 密钥持久化](#15-密钥持久化)
- [16. 配置项汇总](#16-配置项汇总)

---

## 1. 项目模块结构

**父 POM**: `com.example:spring-boot4-oauth2-playground:1.0.0`
- Spring Boot 4.0.5 / Java 21

| 模块 | 路径 | 端口 | 描述 |
|------|------|------|------|
| auth-server | `servers/auth-server` | 9000 | OAuth2 授权服务器 |
| resource-server | `servers/resource-server` | 9001 | OAuth2 资源服务器 |
| oauth2-client | `clients/oauth2-client` | 8080 | OAuth2 客户端 (authorization_code + WebClient) |
| oauth2-client-demo | `clients/oauth2-client-demo` | 8082 | OAuth2 客户端演示 (client_credentials) |
| spa-client | `spa/spa-client` | 3000 | 纯 HTML SPA 公共客户端 (PKCE) |
| spa-client-vue3 | `spa/spa-client-vue3` | 3001 | Vue 3 SPA 公共客户端 (PKCE) |
| spa-client-scripts | `scripts/spa-client-scripts` | - | SPA 静态文件服务脚本 |

### 各模块关键依赖

**auth-server**:
- `spring-boot-starter-oauth2-authorization-server`
- `spring-boot-starter-security` + `spring-boot-starter-web` + `spring-boot-starter-thymeleaf`
- `spring-boot-starter-data-jpa` + `mysql-connector-j` + `spring-boot-starter-data-redis` + `commons-pool2`
- `spring-boot-starter-jdbc`
- `googleauth:1.5.0` (TOTP) + `zxing:core:3.5.1` + `zxing:javase:3.5.1` (QR 码)
- `lombok`

**resource-server**:
- `spring-boot-starter-oauth2-resource-server` + `spring-boot-starter-web` + `lombok`

**oauth2-client**:
- `spring-boot-starter-oauth2-client` + `spring-boot-starter-web` + `spring-boot-starter-thymeleaf`
- `spring-webflux` + `reactor-netty` (WebClient) + `lombok`

---

## 2. auth-server 代码结构

### config 包

| 类名 | 核心职责 |
|------|---------|
| SecurityConfig | 3 条 SecurityFilterChain、CORS/CSP 配置、JWT 自定义 claims、授权存储 Bean、PasswordEncoder、AuthorizationServerSettings |
| RegisteredClientConfig | 注册 4 个 OAuth2 客户端 (oidc-client, resource-server, spa-client, spa-client-vue3)，JdbcRegisteredClientRepository |
| RsaKeyConfig | 从文件加载或生成 RSA 2048 密钥对，持久化到 `~/.config/spring-boot4-oauth2/rsa-key.json` |
| AesKeyConfig | 从文件加载或生成 AES 256 密钥，持久化到 `~/.config/spring-boot4-oauth2/aes-key.key` |
| RedisConfig | RedisTemplate 配置，key 用 StringRedisSerializer，value 用 GenericJacksonJsonRedisSerializer |

### controller 包

| 类名 | 端点 | 职责 |
|------|------|------|
| LoginController | `GET /` `GET /login` | 首页（用户信息+客户端列表）；登录页（已登录则跳转首页） |
| UserController | `GET /users` `GET/POST /users/register` `GET/POST /users/{id}/edit` `POST /users/{id}/password` `POST /users/{id}/delete` `POST /users/{id}/unlock` | 用户 CRUD + 解锁 |
| MfaController | `GET /mfa/setup` `POST /mfa/enable` `POST /mfa/disable` `POST /mfa/regenerate-codes` `GET/POST /mfa/verify` | MFA 设置/验证/恢复码 |
| ConsentController | `GET /oauth2/consent` | 自定义授权同意页，区分已授权 scope 和新 scope |
| TokenRevocationProxyController | `POST /api/revoke` | Token 吊销端点，直接操作 OAuth2AuthorizationService |

### service 包

| 类名 | 核心方法 | 职责 |
|------|---------|------|
| CustomUserDetailsService | `loadUserByUsername()` | 加载用户并检查锁定状态，解析角色 |
| UserService | `findAll()` `findById()` `findByUsername()` `register()` `update()` `changePassword()` `delete()` | 用户 CRUD |
| AccountLockService | `recordFailedAttempt()` `resetFailedAttempts()` `unlock()` `isAccountLocked()` | 账户锁定/解锁/自动解锁（管理员豁免） |
| LoginRateLimitService | `isIpBlocked()` `recordIpFailure()` `resetIpAttempts()` `getRemainingIpAttempts()` | IP 限速（Redis，20次/300秒） |
| MfaService | `generateSecret()` `generateQrCodeBase64()` `verifyCode()` `isCodeValid()` `generateRecoveryCodes()` `consumeRecoveryCode()` | TOTP + QR 码 + 恢复码 |
| AesEncryptionService | `encrypt()` `decrypt()` | AES-256-GCM 加密/解密 TOTP 密钥 |

### entity 包

| 类名 | 关键字段 |
|------|---------|
| User | id, username, password, nickname, email, phone, avatar, enabled, roles, totpSecret, totpEnabled, recoveryCodes, failedAttempts, accountNonLocked, lockedAt, createdAt, updatedAt |

### repository 包

| 类名 | 方法 |
|------|------|
| UserRepository | `findByUsername(String)` → Optional\<User\>；`existsByUsername(String)` → boolean |

### handler 包

| 类名 | 职责 |
|------|------|
| MfaAwareAuthenticationSuccessHandler | 登录成功：重置失败计数和 IP 限速；启用 MFA 则重定向到 `/mfa/verify` |
| LoginFailureHandler | 登录失败：记录 IP/账户失败；区分 LockedException / BadCredentialsException；IP 限速重定向 |

### filter 包

| 类名 | 职责 |
|------|------|
| MfaAuthenticationFilter | OncePerRequestFilter：已认证但未 MFA 验证时重定向到 `/mfa/verify`；白名单: /mfa/\*\*, /login, /logout, /users/register, /css/, /js/, /error |

### util 包

| 类名 | 职责 |
|------|------|
| ClientIpResolver | 安全 IP 解析：可信代理白名单 + X-Forwarded-For 从右向左遍历 + X-Real-IP 兜底 |

---

## 3. resource-server 代码结构

### config 包

| 类名 | 核心职责 |
|------|---------|
| ResourceServerConfig | `/api/public/**` permitAll, `/api/admin/**` hasRole('ADMIN'), `/api/**` authenticated; STATELESS; JWT roles claim 映射; 安全头 |

### controller 包

| 端点 | 权限 | 说明 |
|------|------|------|
| `GET /api/public/hello` | 公开 | 公开接口 |
| `GET /api/user/info` | 已认证 | 用户 JWT 信息 |
| `GET /api/user/messages` | 已认证 | 用户消息 |
| `GET /api/admin/dashboard` | ADMIN | 管理员面板 |
| `GET /api/read/data` | 已认证 | 需要 read scope |
| `GET /api/write/data` | 已认证 | 需要 write scope |

---

## 4. oauth2-client 代码结构

### config 包

| 类名 | 核心职责 |
|------|---------|
| SecurityConfig | oauth2Login (默认跳转 /protected); OIDC Logout; API 请求返回 401 JSON; OAuth2AuthorizedClientManager (authorization_code + refreshToken + clientCredentials); WebClient Bean |

### controller 包

| 端点 | 说明 |
|------|------|
| `GET /` | 首页 |
| `GET /protected` | 受保护页（Token 信息 + Resource Server 数据） |
| `GET /api/user/info` `GET /api/user/messages` | API 代理 |
| `GET /admin` | 管理员页 |
| `POST /api/introspect` | Token 内省代理 |
| `POST /api/revoke-client` | Token 吊销代理 |

---

## 5. oauth2-client-demo 代码结构

仅使用 `.oauth2Client()` (不做用户登录)，STATELESS，演示 client_credentials 模式。

| 端点 | 说明 |
|------|------|
| `GET /` | 首页（可用端点） |
| `GET /demo/client-credentials` | client_credentials 调用 /api/user/info |
| `GET /demo/read-data` | 调用 /api/read/data |
| `GET /demo/admin` | 调用 /api/admin/dashboard（预期失败） |
| `GET /demo/public` | 调用公开接口 |

---

## 6. 数据库

### 表结构

**oauth2_registered_client** — OAuth2 客户端注册信息

| 字段 | 说明 |
|------|------|
| id (PK) | 主键 |
| client_id | 客户端标识 |
| client_secret | 客户端密钥 (BCrypt) |
| client_authentication_methods | 认证方式 |
| authorization_grant_types | 授权类型 |
| redirect_uris | 回调地址 |
| scopes | 权限范围 |
| client_settings / token_settings | 客户端/令牌配置 (JSON) |

**oauth2_authorization** — OAuth2 授权记录

| 字段 | 说明 |
|------|------|
| id (PK) | 主键 |
| registered_client_id | 关联客户端 |
| principal_name | 用户名 |
| authorization_grant_type | 授权类型 |
| access_token_* | Access Token 值/时间/元数据 |
| refresh_token_* | Refresh Token 值/时间/元数据 |
| oidc_id_token_* | ID Token 值/时间/元数据 |
| authorization_code_* | 授权码值/时间/元数据 |

**oauth2_authorization_consent** — 用户授权同意记录

| 字段 | 说明 |
|------|------|
| registered_client_id + principal_name (复合 PK) | 客户端+用户 |
| authorities | 已授权权限 |

**sys_user** — 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint AUTO_INCREMENT | 主键 |
| username | varchar(50) UNIQUE | 用户名 |
| password | varchar(200) | 密码 (BCrypt) |
| nickname | varchar(50) | 昵称 |
| email | varchar(100) | 邮箱 |
| phone | varchar(20) | 手机号 |
| avatar | varchar(255) | 头像 URL |
| enabled | tinyint(1) DEFAULT 1 | 是否启用 |
| roles | varchar(100) DEFAULT 'ROLE_USER' | 角色（逗号分隔） |
| totp_secret | varchar(100) | TOTP 密钥 (AES 加密) |
| totp_enabled | tinyint(1) DEFAULT 0 | 是否启用 MFA |
| recovery_codes | varchar(500) | 恢复码 (JSON) |
| failed_attempts | int DEFAULT 0 | 登录失败次数 |
| account_non_locked | tinyint(1) DEFAULT 1 | 是否未锁定 |
| locked_at | timestamp | 锁定时间 |
| created_at / updated_at | timestamp | 创建/更新时间 |

### 初始数据

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | password | ROLE_ADMIN, ROLE_USER |
| user | password | ROLE_USER |

---

## 7. OAuth2 客户端注册

| Client ID | Secret | 认证方式 | 授权类型 | PKCE | Access Token | Refresh Token | 说明 |
|-----------|--------|----------|----------|------|-------------|---------------|------|
| oidc-client | secret | CLIENT_SECRET_BASIC | authorization_code, refresh_token, client_credentials | 必须 | 3 分钟 | 30 天 (旋转) | Web 客户端 |
| resource-server | secret | CLIENT_SECRET_BASIC | client_credentials | - | 3 分钟 | - | 服务间调用 |
| spa-client | 无 | NONE | authorization_code, refresh_token | 必须 | 3 分钟 | 30 天 (旋转) | SPA 纯 HTML |
| spa-client-vue3 | 无 | NONE | authorization_code, refresh_token | 必须 | 3 分钟 | 30 天 (旋转) | SPA Vue3 |

---

## 8. 安全配置

### auth-server — 3 条 SecurityFilterChain

**Chain 0** (Order=0, matcher: `/api/revoke`):
- permitAll + CSRF 忽略 + CORS

**Chain 1** (Order=1, Authorization Server):
- OAuth2 协议端点 + consentPage `/oauth2/consent` + OIDC + tokenRevocation
- `/oauth2/introspect`, `/oauth2/revoke` permitAll
- CSRF 忽略: /oauth2/authorize, /oauth2/token, /oauth2/revoke, /oauth2/introspect, /connect/logout
- 异常处理: TEXT_HTML → 重定向 /login
- oauth2ResourceServer JWT
- 安全头: XSS + CSP

**Chain 2** (Order=2, Default):
- permitAll: /login, /users/register, /oauth2/consent, /mfa/\*\*, /css/\*\*, /js/\*\*, /error
- formLogin: successHandler=MfaAwareAuthenticationSuccessHandler, failureHandler=LoginFailureHandler
- MfaAuthenticationFilter (after UsernamePasswordAuthenticationFilter)
- 安全头: XSS + CSP + frameOptions sameOrigin

### CORS 允许的来源

```
http://localhost:8100, http://127.0.0.1:8080
http://localhost:3100, http://127.0.0.1:3000
http://localhost:3200, http://127.0.0.1:3001
http://localhost:4173, http://127.0.0.1:4173
```

### CSP 策略

```
default-src 'self';
script-src 'self' 'unsafe-inline';
style-src 'self' 'unsafe-inline';
img-src 'self' data:;
frame-ancestors 'self' http://localhost:3100 http://127.0.0.1:3000 http://localhost:3200 http://127.0.0.1:3001 http://localhost:4173 http://127.0.0.1:4173;
```

---

## 9. JWT 自定义 Claims

| Claim | 来源 | 说明 |
|-------|------|------|
| sub | username | 用户标识 |
| roles | authorities / scope→role | 用户角色列表；client_credentials 模式: admin→ROLE_ADMIN, read→ROLE_READER, write→ROLE_WRITER |
| email | User.email | 邮箱 |
| phone | User.phone | 手机号 |
| nickname | User.nickname | 昵称 |
| preferred_username | User.nickname | OIDC 标准用户名 |
| picture | User.avatar | 头像 URL |
| mfa_enabled | User.totpEnabled | MFA 启用状态 |

---

## 10. 账户锁定与 IP 安全

### 锁定机制

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `auth.lock.max-failed-attempts` | 5 | 连续失败达到该次数后锁定 |
| `auth.lock.auto-unlock-minutes` | 30 | 自动解锁等待时间（分钟） |
| `auth.trusted-proxies` | 空 | 可信代理 IP 列表 |

### 锁定流程

```
登录失败 → LoginFailureHandler
  ├─ LockedException → 重定向 /login?locked
  ├─ BadCredentialsException → AccountLockService.recordFailedAttempt()
  │    ├─ ROLE_ADMIN → 豁免锁定，仅记录日志
  │    └─ failedAttempts >= 5 → accountNonLocked=false, lockedAt=now
  └─ IP 限速检查 → LoginRateLimitService.isIpBlocked()

登录成功 → MfaAwareAuthenticationSuccessHandler
  ├─ AccountLockService.resetFailedAttempts()
  └─ LoginRateLimitService.resetIpAttempts()

加载用户 → CustomUserDetailsService.loadUserByUsername()
  └─ AccountLockService.isAccountLocked()
       └─ 锁定超过30分钟 → 自动解锁

管理员操作 → UserController.unlock()
  └─ AccountLockService.unlock()
```

### 解锁方式

| 方式 | 操作 | 适用场景 |
|------|------|----------|
| 自动解锁 | 等待 30 分钟后下次登录自动解锁 | 常规场景 |
| 管理员页面 | `/users` → 点击「解锁」按钮 | 管理员解锁其他用户 |
| API | `POST /users/{userId}/unlock` | 程序化调用 |
| 数据库 | `UPDATE sys_user SET account_non_locked=1, failed_attempts=0, locked_at=NULL WHERE username='...'` | 管理员自身被锁 |

### 客户端 IP 防伪造

`ClientIpResolver` 安全策略：

| 策略 | 说明 |
|------|------|
| 可信代理白名单 | 仅当 `getRemoteAddr()` 在 `auth.trusted-proxies` 中时才读取 X-Forwarded-For |
| 从右向左遍历 | XFF 格式: `客户端IP, 代理1, 代理2`，跳过所有可信代理取第一个非可信代理 IP |
| X-Real-IP 兜底 | 无有效 XFF 时尝试 X-Real-IP 头 |
| 直连回退 | 以上均无时使用 getRemoteAddr() |

> `trusted-proxies` 为空时完全忽略 X-Forwarded-For，防止伪造。生产环境部署在反向代理后时必须配置此列表。

---

## 11. MFA (TOTP) 两步验证

| 组件 | 技术 |
|------|------|
| TOTP 生成/验证 | googleauth:1.5.0 |
| QR 码生成 | zxing:core:3.5.1 + javase:3.5.1 |
| TOTP Secret 加密存储 | AES-256-GCM (AesEncryptionService) |
| MFA 拦截 | MfaAuthenticationFilter (OncePerRequestFilter) |
| 恢复码 | 10 个 8 位一次性恢复码，支持重新生成 |

### MFA 流程

1. 登录成功 → MfaAwareAuthenticationSuccessHandler 检查 `totpEnabled`
2. 已启用 → session 标记 `MFA_REQUIRED=true`，重定向 `/mfa/verify`
3. 用户输入 TOTP 码或恢复码
4. 验证成功 → session 标记 `MFA_VERIFIED=true`，继续 OAuth2 流程
5. MfaAuthenticationFilter 拦截未验证的已认证请求

---

## 12. Token 管理

### 续期机制

| 机制 | 适用客户端 | 原理 |
|------|-----------|------|
| refresh_token | 机密客户端 + 公共客户端 | 用 refresh_token 换新 access_token |
| Silent Refresh | 公共客户端 (SPA fallback) | iframe + prompt=none 静默重新授权 |

### Token 配置

| 客户端 | Access Token | Refresh Token | 旋转 |
|--------|-------------|---------------|------|
| oidc-client | 3 分钟 | 30 天 | 是 |
| spa-client | 3 分钟 | 30 天 | 是 |
| spa-client-vue3 | 3 分钟 | 30 天 | 是 |

---

## 13. SPA 客户端

### spa-client (纯 HTML + JS, 端口 3000)

| 文件 | 用途 |
|------|------|
| index.html | 首页 + 测试页（Token 展示、MFA 状态、API 调用、Introspection/Revocation） |
| callback.html | 授权回调：解析 code → exchangeCode → 跳转首页 |
| silent-refresh.html | 静默刷新 iframe：postMessage 通知父窗口 |
| js/oauth2-pkce.js | OAuth2 PKCE 核心库 |
| js/app.js | 应用逻辑：倒计时、自动续期、API 调用 |

配置: CLIENT_ID=`spa-client`, SCOPES=`openid profile read write`

### spa-client-vue3 (Vue 3 + Pinia + Axios, 端口 3001)

| 文件 | 用途 |
|------|------|
| src/stores/oauth2.js | Pinia Store：token/claims/expiresAtMs 管理 |
| src/utils/oauth2.js | OAuth2 工具：PKCE + token 交换 + 续期 |
| src/utils/crypto.js | 加密工具：SHA-256 + base64url |
| src/utils/http.js | Axios 实例：authServerClient + resourceServerClient (401 拦截器) |
| src/views/HomeView.vue | 首页：PKCE 流程说明 + 登录 |
| src/views/CallbackView.vue | 回调页 |
| src/views/ProfileView.vue | 测试页：Token + MFA + API + Introspection/Revocation |

配置: CLIENT_ID=`spa-client-vue3`, 依赖: vue 3.5, pinia 3.0, vue-router 4.6, axios 1.16

---

## 14. 模板文件

### auth-server

| 文件 | 用途 |
|------|------|
| home.html | 首页：用户信息 + 客户端列表 |
| login.html | 登录表单：错误/锁定/限速/已注册提示 |
| consent.html | 授权同意页：区分已授权/新增 scope |
| user-register.html | 用户注册表单 |
| user-list.html | 用户管理列表（锁定状态 + 解锁按钮） |
| user-edit.html | 用户编辑表单 |
| mfa-setup.html | MFA 设置：QR 码 + 恢复码 + 启用/禁用 |
| mfa-verify.html | MFA 验证：TOTP 码或恢复码 |

### oauth2-client

| 文件 | 用途 |
|------|------|
| index.html | 客户端首页 |
| protected.html | 受保护页：Token + Resource Server 数据 |
| admin.html | 管理员页面 |

---

## 15. 密钥持久化

| 密钥 | 文件路径 | 说明 |
|------|---------|------|
| RSA 2048 | `~/.config/spring-boot4-oauth2/rsa-key.json` | JWT 签名密钥对，重启后不失效 |
| AES 256 | `~/.config/spring-boot4-oauth2/aes-key.key` | TOTP Secret 加密密钥 |

---

## 16. 配置项汇总

### auth-server

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 9000 | 服务端口 |
| `auth.lock.max-failed-attempts` | 5 | 锁定阈值 |
| `auth.lock.auto-unlock-minutes` | 30 | 自动解锁时间 |
| `auth.trusted-proxies` | 空 | 可信代理 IP 列表 |
| `rsa-key.file` | `~/.config/spring-boot4-oauth2/rsa-key.json` | RSA 密钥文件 |
| `aes-key.file` | `~/.config/spring-boot4-oauth2/aes-key.key` | AES 密钥文件 |
| `spring.datasource.url` | localhost:3306/sb4_auth | MySQL 连接 |
| `spring.data.redis.host` | localhost | Redis 连接 |
| `spring.sql.init.mode` | always | SQL 初始化模式（首次后改 never） |

### resource-server

| 配置项 | 说明 |
|--------|------|
| `server.port` | 9001 |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | http://localhost:9100 |

### oauth2-client

| 配置项 | 说明 |
|--------|------|
| `server.port` | 8080 |
| `spring.security.oauth2.client.registration.my-client.client-id` | oidc-client |
| `resource-server.base-url` | http://localhost:9200 |