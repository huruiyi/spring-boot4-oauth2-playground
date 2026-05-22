package com.example.clientdemo.config;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * 仅使用 .oauth2Client() — 不做用户登录
   * 应用本身作为 OAuth2 客户端，通过 client_credentials 获取令牌
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .anyRequest().permitAll()
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .oauth2Client(oauth2 -> {
          // .oauth2Client() DSL — 配置 OAuth2 客户端能力
          // 与 .oauth2Login() 互斥，不能同时使用
        })
        .csrf(AbstractHttpConfigurer::disable);

    return http.build();
  }

  /**
   * OAuth2AuthorizedClientManager — 管理令牌的获取、缓存、刷新
   * 支持 client_credentials / authorization_code / refresh_token
   */
  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuth2AuthorizedClientService authorizedClientService) {

    var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
        .clientCredentials()    // 以 client_credentials 为主
        .authorizationCode()    // 也支持授权码流程
        .refreshToken()         // 支持刷新令牌
        .build();

    var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
        clientRegistrationRepository, authorizedClientService);
    manager.setAuthorizedClientProvider(authorizedClientProvider);

    return manager;
  }

  /**
   * WebClient — 自动从 OAuth2AuthorizedClientManager 获取令牌并注入请求
   */
  @Bean
  public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
    var filter = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    filter.setDefaultClientRegistrationId("demo-client");

    return WebClient.builder()
        .apply(filter.oauth2Configuration())
        .build();
  }
}
