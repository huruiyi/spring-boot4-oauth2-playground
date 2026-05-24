# OAuth2 常用功能 TODO

## 已实现

- [x] 授权码模式 (authorization_code)
- [x] 客户端模式 (client_credentials)
- [x] 刷新令牌 (refresh_token)
- [x] JWT 令牌签发与验证
- [x] JDBC 存储客户端/授权/同意
- [x] OIDC 基础支持
- [x] 自定义登录页
- [x] 资源服务器 RBAC (hasRole)
- [x] JWT claims scope→role 映射
- [x] .oauth2Login() 客户端
- [x] .oauth2Client() 客户端
- [x] Token 撤销端点 (RFC 7009) — `/oauth2/revoke` 已启用
- [x] OIDC RP-Initiated Logout — `/connect/logout` + client 端清理
- [x] 自定义授权同意页 — `/oauth2/consent` ConsentController + consent.html
- [x] CORS 跨域支持 — auth-server + resource-server 均已配置
- [x] OIDC UserInfo 端点增强 — JWT claims 增加 email/phone/nickname
- [x] 登录成功/失败处理器 — 记录日志 + IP 追踪
- [x] HTTP 安全响应头 — XSS/CSP/FrameOptions 已配置
- [x] PKCE 支持 — `requireProofKey(true)` 授权码+PKCE 流程
- [x] SPA 客户端 (spa-client) — 原生 HTML/JS, 端口 3000
- [x] SPA Vue3 客户端 (spa-client-vue3) — Vite+Vue3+Vue Router, 端口 3001
- [x] Vue Router 导航守卫 — `meta.auth`/`meta.public` 标记, `beforeEach` 拦截
- [x] API 401 拦截器 — Token 无效/过期自动清除 session
- [x] Silent Refresh — `prompt=none` iframe 静默重新授权
- [x] JWT 中文解码修复 — `TextDecoder('utf-8')` 替代 `atob()`
- [x] Token 过期倒计时 — 页面实时显示 Token 剩余有效时间, 到期前自动 Silent Refresh
- [x] Axios 封装 — 替换 fetch, 统一请求/响应拦截器, 自动携带 Bearer Token、401 重定向
- [x] Pinia 状态管理 — 将认证状态从 sessionStorage + 工具函数迁移到 Pinia store, 响应式更新
- [x] PWA 支持 — 离线缓存、安装到桌面
- [x] Token Introspection 演示 — `/oauth2/introspect` 端点验证 Token (三个客户端均已集成)
- [x] Token Revocation 演示 — 自定义 `/api/revoke` 端点吊销 Token (三个客户端均已集成)
- [x] spa-client 功能对齐 Vue3 版本 — 完整布局、样式、Introspection/Revocation
- [x] oauth2-client Token Introspection/Revocation — API 返回当前 token 对比自动刷新状态
- [x] API 未认证返回 401 JSON — exceptionHandling + RequestMatcher

## 待实现

### 功能完善

- [ ] 暗黑模式 — ProfileView 添加主题切换
- [ ] i18n 国际化 — 中英文切换, 登录页/测试页/错误提示多语言
- [ ] TypeScript 迁移 — spa-client-vue3 迁移为 TS, oauth2.js 类型化
- [ ] 用户头像 — 从 ID Token 的 `picture` claim 或 Gravatar 获取头像

### 开发体验

- [ ] 单元测试 — Vitest 测试 oauth2.js 的 PKCE、JWT 解码、Token 检测
- [ ] ESLint + Prettier — 代码规范和格式化

### OAuth2 扩展

- [ ] Device Flow — 设备码流程（IoT/CLI 场景）
- [ ] MFA 演示 — 多因素认证集成

---

## Device Flow 实施计划

### 原理（RFC 8628）

适用于输入受限设备（智能电视、IoT、CLI）。流程：
1. 设备请求 → auth-server 返回 `device_code` + `user_code` + `verification_uri`
2. 设备显示 `user_code`，用户在另一设备访问 `verification_uri` 输入 `user_code` 授权
3. 设备轮询 auth-server 直到用户完成授权，获取 token

### Spring Authorization Server 支持

Spring Authorization Server **1.1+** 原生支持 Device Flow：
- `DeviceCode` grant type 自动注册
- 端点：`POST /oauth2/device_authorization`（设备发起）+ `POST /oauth2/token?grant_type=urn:ietf:params:oauth:grant-type:device_code`（设备轮询）
- 需要用户验证页面（`/oauth2/device_verification`）让用户输入 `user_code` 并确认

### 需要引入的组件

| 组件 | 用途 |
|------|------|
| auth-server 注册 `device-client` | `ClientAuthenticationMethod.NONE` + `DeviceCode` grant type |
| 设备客户端页面 | 模拟设备端：请求 device_code → 显示 user_code → 轮询 token |
| 用户验证页面 | 用户访问 verification_uri 输入 user_code 授权 |
| `RegisteredClient` 配置 | `.authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)` |

### 实施步骤

```
Phase 1: Device Flow
  ├── 1.1 auth-server 注册 device-client (DeviceCode grant type)
  ├── 1.2 auth-server 自定义 device_verification 页面
  ├── 1.3 新增 device-client 纯 HTML 模拟页面
  └── 1.4 测试完整 Device Flow
```

预计工作量：~2h

---

## MFA (TOTP) 实施计划

### 方案对比

| 方案 | 复杂度 | 依赖 | 适合演示 |
|------|--------|------|---------|
| **TOTP**（Google Authenticator） | 中 | `googleauth` + `zxing`(QR码) | 最佳 |
| **WebAuthn/FIDO2** | 高 | `spring-security-webauthn`(实验性) | 不推荐 |
| **SMS/Email OTP** | 中 | 邮件/短信服务 | 需外部服务 |

### 推荐方案：TOTP

流程：
1. 用户登录 → 检查是否绑定 TOTP
2. 未绑定 → 显示 QR 码（含 TOTP secret），用户用 Google Authenticator 扫码绑定
3. 已绑定 → 登录后跳转 MFA 验证页，输入 6 位动态码
4. 验证通过 → 完成登录

### 需要引入的组件

| 组件 | 用途 |
|------|------|
| `com.warrenstrange:googleauth` (1.5+) | TOTP 生成/验证核心库 |
| `com.google.zxing:core` + `javase` (3.5+) | QR 码生成（将 TOTP secret 编码为 otpauth:// URI） |
| 自定义 `UserDetailsService` | 扩展用户模型，增加 totpSecret / totpEnabled 字段 |
| 自定义 MFA 验证页 | `/mfa/verify`：6 位码输入 + 验证逻辑 |
| 自定义 MFA 绑定页 | `/mfa/setup`：显示 QR 码 + 手动输入备用 |
| `SecurityFilterChain` 调整 | 登录后拦截未 MFA 验证的 session |
| Session 属性 | `MFA_VERIFIED` 标记当前 session 已通过 MFA |

### 实施步骤

```
Phase 2: MFA (TOTP)
  ├── 2.1 auth-server pom.xml 添加 googleauth + zxing 依赖
  ├── 2.2 扩展用户模型 (totpSecret / totpEnabled)
  ├── 2.3 实现 /mfa/setup 端点 (生成 secret + QR 码)
  ├── 2.4 实现 /mfa/verify 端点 (验证 TOTP 码)
  ├── 2.5 SecurityFilterChain: 登录后检查 MFA 状态
  ├── 2.6 自定义 MFA 验证页和绑定页模板
  └── 2.7 测试完整 MFA 流程
```

预计工作量：~5h
