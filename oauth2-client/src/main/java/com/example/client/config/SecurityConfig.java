package com.example.client.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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
              log.error("OAuth2 登录失败: {}", exception.getMessage(), exception);
              response.sendRedirect("/?error");
            })
        )
        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies("JSESSIONID")
            .addLogoutHandler((request, response, authentication) -> {
              // 构造 OIDC RP-Initiated Logout URL
              // id_token_hint 从当前 session 中获取
              if (authentication != null) {
                try {
                  var authClientRepo = http.getSharedObject(OAuth2AuthorizedClientRepository.class);
                  if (authClientRepo != null) {
                    var authorizedClient = authClientRepo.loadAuthorizedClient(
                        "my-client", authentication, request);
                    if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                      String idToken = authorizedClient.getAccessToken().getTokenValue();
                      String logoutUrl = "http://localhost:9000/connect/logout" +
                          "?post_logout_redirect_uri=http://localhost:8080/" +
                          "&id_token_hint=" + idToken;
                      log.info("Redirecting to OIDC logout: {}", logoutUrl);
                      // 不直接重定向，交给 LogoutSuccessHandler 处理
                    }
                    // 清除已授权的客户端
                    authClientRepo.removeAuthorizedClient("my-client", authentication, request, response);
                  }
                } catch (Exception e) {
                  log.warn("清理 OAuth2 客户端失败: {}", e.getMessage());
                }
              }
              new SecurityContextLogoutHandler().logout(request, response, authentication);
            })
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
