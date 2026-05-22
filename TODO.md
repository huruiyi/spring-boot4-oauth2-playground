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
