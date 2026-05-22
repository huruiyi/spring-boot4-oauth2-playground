package com.example.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository =
      new CookieOAuth2AuthorizationRequestRepository();

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/", "/public/**", "/css/**", "/js/**", "/oauth2/authorization/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth2 -> oauth2
            .loginPage("/")
            .defaultSuccessUrl("/protected", true)
            .authorizationEndpoint(endpoint -> endpoint
                .authorizationRequestRepository(authorizationRequestRepository)
            )
            .failureHandler((request, response, exception) -> {
              log.error("===== OAuth2 登录失败 =====");
              log.error("请求 URL: {}", request.getRequestURL());
              log.error("查询参数: {}", request.getQueryString());
              log.error("异常类型: {}", exception.getClass().getName());
              log.error("异常信息: {}", exception.getMessage());
              if (exception.getCause() != null) {
                log.error("根因异常类型: {}", exception.getCause().getClass().getName());
                log.error("根因异常信息: {}", exception.getCause().getMessage());
              }
              log.error("完整堆栈:", exception);
              response.sendRedirect("/?error");
            })
        )
        .logout(logout -> logout
            .logoutSuccessUrl("/")
        );

    return http.build();
  }

  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuth2AuthorizedClientRepository authorizedClientRepository) {

    var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
        .authorizationCode()
        .refreshToken()
        .clientCredentials()
        .build();

    var authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
        clientRegistrationRepository, authorizedClientRepository);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }

  @Bean
  public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
    ServletOAuth2AuthorizedClientExchangeFilterFunction filter =
        new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    filter.setDefaultOAuth2AuthorizedClient(true);

    return WebClient.builder()
        .apply(filter.oauth2Configuration())
        .build();
  }
}
