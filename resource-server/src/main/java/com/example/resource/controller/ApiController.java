package com.example.resource.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

  /**
   * 公开接口 - 无需认证
   */
  @GetMapping("/public/hello")
  public Map<String, Object> publicHello() {
    return Map.of(
        "message", "这是一个公开接口，无需认证即可访问",
        "timestamp", System.currentTimeMillis()
    );
  }

  /**
   * 受保护接口 - 需要认证（兼容 client_credentials 无 roles/scopes 的场景）
   */
  @GetMapping("/user/info")
  public Map<String, Object> userInfo(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "username", jwt.getSubject() != null ? jwt.getSubject() : "N/A",
        "roles", jwt.getClaimAsStringList("roles") != null ? jwt.getClaimAsStringList("roles") : Collections.emptyList(),
        "scopes", jwt.getClaimAsStringList("scope") != null ? jwt.getClaimAsStringList("scope") : Collections.emptyList(),
        "issuer", jwt.getIssuer() != null ? jwt.getIssuer().toString() : "N/A",
        "issuedAt", jwt.getIssuedAt() != null ? jwt.getIssuedAt().toString() : "N/A",
        "expiresAt", jwt.getExpiresAt() != null ? jwt.getExpiresAt().toString() : "N/A"
    );
  }

  /**
   * 受保护接口 - 需要认证
   */
  @GetMapping("/user/messages")
  public Map<String, Object> userMessages(@AuthenticationPrincipal Jwt jwt) {
    String username = jwt.getSubject() != null ? jwt.getSubject() : "unknown";
    return Map.of(
        "username", username,
        "messages", new String[]{
            "你好，" + username + "！",
            "这是一条受保护的系统消息。",
            "只有认证用户可以查看此消息。"
        }
    );
  }

  /**
   * 管理员接口 - 需要 ADMIN 角色
   */
  @GetMapping("/admin/dashboard")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Object> adminDashboard(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "message", "欢迎进入管理员面板",
        "admin", jwt.getSubject() != null ? jwt.getSubject() : "N/A",
        "stats", Map.of(
            "totalUsers", 1024,
            "activeTokens", 56,
            "serverStatus", "RUNNING"
        )
    );
  }

  /**
   * 需要read权限的接口
   */
  @GetMapping("/read/data")
  public Map<String, Object> readData(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "data", "敏感读取数据 - 需要 read scope",
        "user", jwt.getSubject() != null ? jwt.getSubject() : "unknown"
    );
  }

  /**
   * 需要write权限的接口
   */
  @GetMapping("/write/data")
  public Map<String, Object> writeData(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "data", "写入操作数据 - 需要 write scope",
        "user", jwt.getSubject() != null ? jwt.getSubject() : "unknown"
    );
  }
}
