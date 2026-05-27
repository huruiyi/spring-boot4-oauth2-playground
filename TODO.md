# OAuth2 常用功能 TODO

## 已实现

### OAuth2/OIDC 核心协议

- [x] 授权码模式 (authorization_code)
- [x] 客户端模式 (client_credentials)
- [x] 刷新令牌 (refresh_token)
- [x] JWT 令牌签发与验证
- [x] JDBC 存储客户端/授权/同意
- [x] OIDC 基础支持
- [x] PKCE 支持 — `requireProofKey(true)` 授权码+PKCE 流程
- [x] Token 撤销端点 (RFC 7009) — `/oauth2/revoke` 已启用
- [x] OIDC RP-Initiated Logout — `/connect/logout` + client 端清理
- [x] Token Introspection 演示 — `/oauth2/introspect` 端点验证 Token (三个客户端均已集成)
- [x] Token Revocation 演示 — 自定义 `/api/revoke` 端点吊销 Token (三个客户端均已集成)

### 安全增强

- [x] 自定义登录页
- [x] 自定义授权同意页 — `/oauth2/consent` ConsentController + consent.html
- [x] 资源服务器 RBAC (hasRole)
- [x] JWT claims scope→role 映射
- [x] 登录成功/失败处理器 — 记录日志 + IP 追踪
- [x] HTTP 安全响应头 — XSS/CSP/FrameOptions 已配置
- [x] CORS 跨域支持 — auth-server + resource-server 均已配置
- [x] OIDC UserInfo 端点增强 — JWT claims 增加 email/phone/nickname/picture
- [x] MFA (TOTP) 两步验证 — googleauth + zxing QR码 + 恢复码
- [x] MFA 恢复码 — 10个8位一次性恢复码，支持重新生成
- [x] MFA 登录拦截 — MfaAuthenticationFilter + MfaAwareAuthenticationSuccessHandler
- [x] JWT claims mfa_enabled — ID Token 包含 MFA 启用状态
- [x] JWT claims picture — User avatar 字段输出为 OIDC picture claim

### 客户端应用

- [x] .oauth2Login() 客户端 (oauth2-client, 端口 8080)
- [x] .oauth2Client() 客户端
- [x] SPA 客户端 (spa-client) — 原生 HTML/JS, 端口 3000
- [x] SPA Vue3 客户端 (spa-client-vue3) — Vite+Vue3+Vue Router, 端口 3001
- [x] Vue Router 导航守卫 — `meta.auth`/`meta.public` 标记, `beforeEach` 拦截
- [x] API 401 拦截器 — Token 无效/过期自动清除 session
- [x] Axios 封装 — 替换 fetch, 统一请求/响应拦截器, 自动携带 Bearer Token、401 重定向
- [x] Pinia 状态管理 — 将认证状态从 sessionStorage + 工具函数迁移到 Pinia store, 响应式更新

### Token 管理

- [x] Silent Refresh — `prompt=none` iframe 静默重新授权
- [x] JWT 中文解码修复 — `TextDecoder('utf-8')` 替代 `atob()`
- [x] Token 过期倒计时 — 页面实时显示 Token 剩余有效时间, 到期前自动 Silent Refresh
- [x] PWA 支持 — 离线缓存、安装到桌面
- [x] spa-client 功能对齐 Vue3 版本 — 完整布局、样式、Introspection/Revocation
- [x] oauth2-client Token Introspection/Revocation — API 返回当前 token 对比自动刷新状态
- [x] API 未认证返回 401 JSON — exceptionHandling + RequestMatcher

### UI/UX

- [x] auth-server 首页 — 用户信息 + MFA 状态 + OAuth2 端点 + 已注册客户端
- [x] spa-client MFA 集成 — MFA 状态卡片 + 管理入口 + mfa_enabled claim 展示
- [x] spa-client-vue3 MFA 集成 — ProfileView MFA 状态卡片 + 管理入口
- [x] MFA 页面样式优化 — 统一按钮系统 + 垂直按钮组 + Tailwind 色板
- [x] picture claim 展示 — spa-client + spa-client-vue3 claims 表格均已包含
- [x] spa-client-vue3 样式统一 — HomeView/ProfileView 对齐 spa-client 基准风格（卡片/badge/按钮）
- [x] spa-client-scripts 迁移 — 服务脚本从 spa-client/scripts/ 提取为顶层独立目录

### 项目结构

- [x] 组件提取 — MfaAwareAuthenticationSuccessHandler、RegisteredClientConfig 从 SecurityConfig 拆出
- [x] CSP 修复 — img-src data: (base64 QR码) + script-src/style-src unsafe-inline
- [x] MFA 无限循环修复 — MFA filter 仅拦截 MFA_REQUIRED session 标记
- [x] auth-server 首页映射 — `/` 路径 LoginController + home.html
- [x] RSA 密钥持久化 — RsaKeyConfig 从文件加载或生成并保存，重启后 JWT 不失效
- [x] TOTP Secret 加密存储 — AES-256-GCM 加密，密钥从用户配置目录加载

## 未实现（7 项）

| # | 功能 | 分类 | 优先级 | 说明 |
|---|------|------|--------|------|
| 1 | Device Flow | OAuth2 扩展 | 中 | 设备码流程（IoT/CLI），schema 已预留 device_code 字段 |
| 2 | 暗黑模式 | 功能完善 | 低 | ProfileView 添加主题切换，需 CSS 变量 + localStorage |
| 3 | i18n 国际化 | 功能完善 | 中 | 中英文切换，登录页/测试页/错误提示多语言 |
| 4 | TypeScript 迁移 | 功能完善 | 中 | spa-client-vue3 迁移为 TS，oauth2.js 类型化 |
| 5 | 单元测试 + ESLint/Prettier | 开发体验 | 高 | Vitest 测试 oauth2.js PKCE/JWT 解码；代码规范和格式化 |
| 6 | CSP nonce 替代 unsafe-inline | 安全加固 | 中 | 当前 CSP 使用 'unsafe-inline' 削弱 XSS 防护 |
| 7 | 用户头像 UI 展示 | 功能完善 | 低 | JWT 已输出 picture claim，前端未渲染头像图片 |

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
