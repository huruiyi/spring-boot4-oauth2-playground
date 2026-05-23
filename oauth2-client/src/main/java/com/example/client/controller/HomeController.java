package com.example.client.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

  private final WebClient webClient;

  @Value("${resource-server.base-url:http://localhost:9001}")
  private String resourceServerBaseUrl;

  @GetMapping("/")
  public String index() {
    return "index";
  }

  @GetMapping("/protected")
  public String protectedPage(
      Model model,
      @AuthenticationPrincipal OAuth2User oAuth2User,
      @RegisteredOAuth2AuthorizedClient("my-client") OAuth2AuthorizedClient authorizedClient) {

    // 优先使用 OIDC preferred_username，否则回退到 name 属性
    String username = oAuth2User.getAttribute("preferred_username");
    if (username == null) {
      username = oAuth2User.getName();
    }
    model.addAttribute("username", username);

    // Access Token
    model.addAttribute("accessToken", authorizedClient.getAccessToken().getTokenValue());
    model.addAttribute("accessTokenScopes", authorizedClient.getAccessToken().getScopes());
    model.addAttribute("accessTokenIssuedAt", authorizedClient.getAccessToken().getIssuedAt());
    model.addAttribute("accessTokenExpiresAt", authorizedClient.getAccessToken().getExpiresAt());
    model.addAttribute("accessTokenTokenType", authorizedClient.getAccessToken().getTokenType().getValue());

    // Refresh Token
    model.addAttribute("hasRefreshToken", authorizedClient.getRefreshToken() != null);
    if (authorizedClient.getRefreshToken() != null) {
      model.addAttribute("refreshTokenValue", authorizedClient.getRefreshToken().getTokenValue());
      model.addAttribute("refreshTokenIssuedAt", authorizedClient.getRefreshToken().getIssuedAt());
      model.addAttribute("refreshTokenExpiresAt", authorizedClient.getRefreshToken().getExpiresAt());
    }

    // ID Token
    OidcIdToken idToken = ((DefaultOidcUser) oAuth2User).getIdToken();
    model.addAttribute("idTokenValue", idToken.getTokenValue());
    model.addAttribute("idTokenClaims", idToken.getClaims());
    model.addAttribute("idTokenIssuedAt", idToken.getIssuedAt());
    model.addAttribute("idTokenExpiresAt", idToken.getExpiresAt());

    // 调用 Resource Server 获取用户信息
    try {
      Map userInfo = webClient.get()
          .uri(resourceServerBaseUrl + "/api/user/info")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      model.addAttribute("userInfo", userInfo);
    } catch (Exception e) {
      log.error("调用资源服务器失败", e);
      model.addAttribute("userInfoError", e.getMessage());
    }

    // 调用 Resource Server 获取消息
    try {
      Map messages = webClient.get()
          .uri(resourceServerBaseUrl + "/api/user/messages")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      model.addAttribute("messages", messages);
    } catch (Exception e) {
      log.error("调用消息接口失败", e);
      model.addAttribute("messagesError", e.getMessage());
    }

    return "protected";
  }

  @GetMapping("/admin")
  public String adminPage(Model model,
                          @RegisteredOAuth2AuthorizedClient("my-client") OAuth2AuthorizedClient authorizedClient) {
    try {
      Map adminData = webClient.get()
          .uri(resourceServerBaseUrl + "/api/admin/dashboard")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      model.addAttribute("adminData", adminData);
    } catch (Exception e) {
      log.error("调用管理接口失败", e);
      model.addAttribute("adminError", e.getMessage());
    }
    return "admin";
  }
}
