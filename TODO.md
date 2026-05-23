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

## 待实现

### 安全增强

- [ ] Token 过期倒计时 — 页面实时显示 Token 剩余有效时间, 到期前自动 Silent Refresh
- [ ] Axios 封装 — 替换 fetch, 统一请求/响应拦截器, 自动携带 Bearer Token、401 重定向
- [ ] Pinia 状态管理 — 将认证状态从 sessionStorage + 工具函数迁移到 Pinia store, 响应式更新

### 功能完善

- [ ] 暗黑模式 — ProfileView 添加主题切换
- [ ] i18n 国际化 — 中英文切换, 登录页/测试页/错误提示多语言
- [ ] TypeScript 迁移 — spa-client-vue3 迁移为 TS, oauth2.js 类型化
- [ ] 用户头像 — 从 ID Token 的 `picture` claim 或 Gravatar 获取头像

### 开发体验

- [ ] 单元测试 — Vitest 测试 oauth2.js 的 PKCE、JWT 解码、Token 检测
- [ ] ESLint + Prettier — 代码规范和格式化
- [ ] PWA 支持 — 离线缓存、安装到桌面

### OAuth2 扩展

- [ ] Token Introspection 演示 — `/oauth2/introspect` 端点验证 Token
- [ ] Token Revocation 演示 — `/oauth2/revoke` 端点撤销 Token（SPA 集成）
- [ ] Device Flow — 设备码流程（IoT/CLI 场景）
- [ ] MFA 演示 — 多因素认证集成
