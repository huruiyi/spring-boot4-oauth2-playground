package com.example.client.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

  private final WebClient webClient;
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final ClientRegistrationRepository clientRegistrationRepository;

  @Value("${auth-server.base-url:http://localhost:9100}")
  private String authServerBaseUrl;

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private String formatInstant(Instant instant) {
    return instant != null ? FMT.format(instant) : "-";
  }

  @Value("${resource-server.base-url:http://localhost:9200}")
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
    model.addAttribute("accessTokenIssuedAt", formatInstant(authorizedClient.getAccessToken().getIssuedAt()));
    model.addAttribute("accessTokenExpiresAt", formatInstant(authorizedClient.getAccessToken().getExpiresAt()));
    model.addAttribute("accessTokenExpiresAtMs", authorizedClient.getAccessToken().getExpiresAt() != null
        ? authorizedClient.getAccessToken().getExpiresAt().toEpochMilli() : 0);
    model.addAttribute("accessTokenTokenType", authorizedClient.getAccessToken().getTokenType().getValue());

    // Refresh Token
    model.addAttribute("hasRefreshToken", authorizedClient.getRefreshToken() != null);
    if (authorizedClient.getRefreshToken() != null) {
      model.addAttribute("refreshTokenValue", authorizedClient.getRefreshToken().getTokenValue());
      model.addAttribute("refreshTokenIssuedAt", formatInstant(authorizedClient.getRefreshToken().getIssuedAt()));
      model.addAttribute("refreshTokenExpiresAt", formatInstant(authorizedClient.getRefreshToken().getExpiresAt()));
    }

    // ID Token
    OidcIdToken idToken = ((DefaultOidcUser) oAuth2User).getIdToken();
    model.addAttribute("idTokenValue", idToken.getTokenValue());
    model.addAttribute("idTokenClaims", idToken.getClaims());
    model.addAttribute("idTokenIssuedAt", formatInstant(idToken.getIssuedAt()));
    model.addAttribute("idTokenExpiresAt", formatInstant(idToken.getExpiresAt()));

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

  @GetMapping("/api/user/info")
  @org.springframework.web.bind.annotation.ResponseBody
  public Map<String, Object> userInfoApi(
      @RegisteredOAuth2AuthorizedClient("my-client") OAuth2AuthorizedClient authorizedClient) {
    String currentToken = authorizedClient.getAccessToken().getTokenValue();
    try {
      Map data = webClient.get()
          .uri(resourceServerBaseUrl + "/api/user/info")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("currentAccessToken", currentToken);
      result.put("data", data);
      return result;
    } catch (Exception e) {
      return Map.of("error", e.getMessage(), "currentAccessToken", currentToken);
    }
  }

  @GetMapping("/api/user/messages")
  @org.springframework.web.bind.annotation.ResponseBody
  public Map<String, Object> userMessagesApi(
      @RegisteredOAuth2AuthorizedClient("my-client") OAuth2AuthorizedClient authorizedClient) {
    String currentToken = authorizedClient.getAccessToken().getTokenValue();
    try {
      Map data = webClient.get()
          .uri(resourceServerBaseUrl + "/api/user/messages")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      Map<String, Object> result = new java.util.LinkedHashMap<>();
      result.put("currentAccessToken", currentToken);
      result.put("data", data);
      return result;
    } catch (Exception e) {
      return Map.of("error", e.getMessage(), "currentAccessToken", currentToken);
    }
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

  @PostMapping("/api/introspect")
  @ResponseBody
  public Map<String, Object> introspectToken(
      @RequestBody Map<String, String> body,
      @RegisteredOAuth2AuthorizedClient("my-client") OAuth2AuthorizedClient authorizedClient) {
    String token = body.get("token");
    String tokenTypeHint = body.get("token_type_hint");
    if (token == null || token.isBlank()) {
      return Map.of("error", "token is required");
    }

    try {
      String clientId = authorizedClient.getClientRegistration().getClientId();
      String clientSecret = authorizedClient.getClientRegistration().getClientSecret();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      headers.setBasicAuth(clientId, clientSecret);

      MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add("token", token);
      if (tokenTypeHint != null) params.add("token_type_hint", tokenTypeHint);

      RestTemplate restTemplate = new RestTemplate();
      @SuppressWarnings("unchecked")
      Map<String, Object> result = restTemplate.postForObject(
          authServerBaseUrl + "/oauth2/introspect",
          new HttpEntity<>(params, headers),
          Map.class
      );
      return result != null ? result : Map.of("error", "empty response");
    } catch (Exception e) {
      log.error("Introspection 失败", e);
      return Map.of("error", e.getMessage());
    }
  }

  @PostMapping("/api/revoke-client")
  @ResponseBody
  public Map<String, Object> revokeToken(
      @RequestBody Map<String, Object> body,
      @RegisteredOAuth2AuthorizedClient("my-client") OAuth2AuthorizedClient authorizedClient,
      @AuthenticationPrincipal OAuth2User oAuth2User) {
    String token = (String) body.get("token");
    String tokenTypeHint = (String) body.get("token_type_hint");
    boolean removeClient = body.get("removeClient") != null && (boolean) body.get("removeClient");
    if (token == null || token.isBlank()) {
      return Map.of("error", "token is required");
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      Map<String, String> revokeBody = new java.util.HashMap<>();
      revokeBody.put("token", token);
      if (tokenTypeHint != null) revokeBody.put("token_type_hint", tokenTypeHint);

      RestTemplate restTemplate = new RestTemplate();
      restTemplate.postForObject(
          authServerBaseUrl + "/api/revoke",
          new HttpEntity<>(revokeBody, headers),
          Void.class
      );

      if (removeClient) {
        String principalName = oAuth2User.getName();
        String registrationId = authorizedClient.getClientRegistration().getRegistrationId();
        authorizedClientService.removeAuthorizedClient(registrationId, principalName);
        return Map.of("status", "revoked", "message", "Token 已吊销，已移除本地授权客户端，请重新登录");
      }

      return Map.of("status", "revoked", "message", "Token 已在服务器吊销，本地授权客户端保留（JWT 无状态，resource-server 可能仍接受此 token）");
    } catch (Exception e) {
      log.error("Revocation 失败", e);
      return Map.of("error", e.getMessage());
    }
  }
}
