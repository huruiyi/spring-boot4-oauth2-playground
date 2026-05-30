package com.example.clientdemo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DemoController {

  private final WebClient webClient;

  @Value("${resource-server.base-url:http://localhost:9200}")
  private String resourceServerBaseUrl;

  @GetMapping("/")
  public String index(Model model) {
    model.addAttribute("title", ".oauth2Client() 演示");
    model.addAttribute("endpoints", new String[][]{
        {"/demo/client-credentials", "client_credentials 模式 → 调用 /api/user/info"},
        {"/demo/read-data", "client_credentials 模式 → 调用 /api/read/data"},
        {"/demo/admin", "client_credentials 模式 → 调用 /api/admin/dashboard"},
        {"/demo/public", "直接调用公开接口 /api/public/hello"},
    });
    return "demo-index";
  }

  /**
   * client_credentials 模式：应用以自身身份获取令牌，调用受保护的资源接口
   * WebClient 通过 ServletOAuth2AuthorizedClientExchangeFilterFunction 自动管理令牌
   */
  @GetMapping("/demo/client-credentials")
  public String callWithClientCredentials(Model model) {
    model.addAttribute("mode", "client_credentials");
    model.addAttribute("description", "应用以自身身份（demo-client）获取令牌，无需用户参与");

    try {
      Map<String, Object> result = webClient.get()
          .uri(resourceServerBaseUrl + "/api/user/info")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      model.addAttribute("success", true);
      model.addAttribute("data", result);
    } catch (Exception e) {
      log.error("client_credentials 调用失败", e);
      model.addAttribute("success", false);
      model.addAttribute("error", e.getMessage());
    }

    return "demo-result";
  }

  /**
   * client_credentials 模式调用 read 权限接口
   */
  @GetMapping("/demo/read-data")
  public String callReadData(Model model) {
    model.addAttribute("mode", "client_credentials");
    model.addAttribute("description", "调用 /api/read/data（需要 read scope）");

    try {
      Map<String, Object> result = webClient.get()
          .uri(resourceServerBaseUrl + "/api/read/data")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      model.addAttribute("success", true);
      model.addAttribute("data", result);
    } catch (Exception e) {
      log.error("read data 调用失败", e);
      model.addAttribute("success", false);
      model.addAttribute("error", e.getMessage());
    }

    return "demo-result";
  }

  /**
   * client_credentials 模式调用 admin 接口（需要 ADMIN 角色才可能失败）
   */
  @GetMapping("/demo/admin")
  public String callAdmin(Model model) {
    model.addAttribute("mode", "client_credentials");
    model.addAttribute("description", "调用 /api/admin/dashboard（需要 ADMIN 角色）");

    try {
      Map<String, Object> result = webClient.get()
          .uri(resourceServerBaseUrl + "/api/admin/dashboard")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      model.addAttribute("success", true);
      model.addAttribute("data", result);
    } catch (Exception e) {
      log.error("admin 调用失败（预期行为：client_credentials 没有用户角色）", e);
      model.addAttribute("success", false);
      model.addAttribute("error", e.getMessage());
      model.addAttribute("note", "预期失败：client_credentials 模式下没有用户角色信息");
    }

    return "demo-result";
  }

  /**
   * 调用公开接口（不需要令牌）
   */
  @GetMapping("/demo/public")
  public String callPublic(Model model) {
    model.addAttribute("mode", "无需认证");
    model.addAttribute("description", "直接调用公开接口 /api/public/hello");

    try {
      Map<String, Object> result = webClient.get()
          .uri(resourceServerBaseUrl + "/api/public/hello")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
      model.addAttribute("success", true);
      model.addAttribute("data", result);
    } catch (Exception e) {
      log.error("公开接口调用失败", e);
      model.addAttribute("success", false);
      model.addAttribute("error", e.getMessage());
    }

    return "demo-result";
  }
}
