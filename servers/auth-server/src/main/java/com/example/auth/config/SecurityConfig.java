package com.example.auth.config;

import com.example.auth.entity.User;
import com.example.auth.filter.MfaAuthenticationFilter;
import com.example.auth.handler.LoginFailureHandler;
import com.example.auth.handler.MfaAwareAuthenticationSuccessHandler;
import com.example.auth.repository.UserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {


  private final UserRepository userRepository;

  public SecurityConfig(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  // ==================== Security Filter Chains ====================

  @Bean
  @Order(0)
  public SecurityFilterChain apiRevokeSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/revoke")
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(csrf -> csrf.ignoringRequestMatchers("/api/revoke"))
        .cors(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
    http
        .oauth2AuthorizationServer(authorizationServer -> {
          http.securityMatcher(authorizationServer.getEndpointsMatcher());
          authorizationServer
              .authorizationEndpoint(endpoint -> endpoint
                  .consentPage("/oauth2/consent")
              )
              .oidc(oidc -> oidc
                  .userInfoEndpoint(Customizer.withDefaults())
              )
              .tokenRevocationEndpoint(Customizer.withDefaults());
        })
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/oauth2/introspect", "/oauth2/revoke").permitAll()
            .anyRequest().authenticated()
        )
        .csrf(csrf -> csrf
            .ignoringRequestMatchers(
                "/oauth2/authorize",
                "/oauth2/token",
                "/oauth2/revoke",
                "/oauth2/introspect",
                "/connect/logout"
            )
        )
        .cors(Customizer.withDefaults())
        .exceptionHandling(exceptions -> exceptions
            .defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
            )
        )
        .oauth2ResourceServer(resourceServer -> resourceServer
            .jwt(Customizer.withDefaults())
        )
        .headers(headers -> headers
            .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'self' http://localhost:3100 http://127.0.0.1:3100 http://localhost:3200 http://127.0.0.1:3200 http://localhost:4173 http://127.0.0.1:4173;"))
        );

    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, 
      MfaAuthenticationFilter mfaAuthenticationFilter,
      MfaAwareAuthenticationSuccessHandler mfaAwareSuccessHandler,
      LoginFailureHandler loginFailureHandler) throws Exception {
    http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/login", "/users/register", "/oauth2/consent", "/mfa/**", "/css/**", "/js/**", "/error").permitAll()
        .anyRequest().authenticated()
    );
    http.formLogin(form -> form
        .loginPage("/login")
        .permitAll()
        .successHandler(mfaAwareSuccessHandler)
        .failureHandler(loginFailureHandler)
    );
    http.addFilterAfter(mfaAuthenticationFilter, 
        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
    http.cors(Customizer.withDefaults());
    http.headers(headers -> headers
        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
        .contentSecurityPolicy(csp -> csp.policyDirectives(
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'self' http://localhost:3100 http://127.0.0.1:3100 http://localhost:3200 http://127.0.0.1:3200 http://localhost:4173 http://127.0.0.1:4173;"))
        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
    );

    return http.build();
  }

  // ==================== CORS ====================

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins = List.of(
        "http://localhost:8100",
        "http://127.0.0.1:8100",
        "http://localhost:8200",
        "http://127.0.0.1:8200",
        "http://localhost:3100",
        "http://127.0.0.1:3100",
        "http://localhost:3200",
        "http://127.0.0.1:3200",
        "http://localhost:4173",
        "http://127.0.0.1:4173");
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  // ==================== 授权存储 ====================

  @Bean
  public OAuth2AuthorizationService authorizationService(
      JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
    return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
  }

  @Bean
  public OAuth2AuthorizationConsentService authorizationConsentService(
      JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
    return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
  }

  // ==================== JWT 配置 ====================

  @Bean
  public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
  }

  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return NimbusJwtDecoder.withJwkSource(jwkSource).build();
  }

  /**
   * JWT 自定义 claims — 用户角色 + 用户 profile + scope→role 映射
   */
  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
    return context -> {
      if (context.getPrincipal() != null) {
        var principal = context.getPrincipal();
        String username = principal.getName();
        context.getClaims().claim("sub", username);

        var roles = new java.util.ArrayList<String>();

        // 用户角色（authorization_code 登录流程）
        var authorities = principal.getAuthorities();
        if (authorities != null && !authorities.isEmpty()) {
          authorities.forEach(auth -> roles.add(auth.getAuthority()));
        }

        // scope → role 映射（client_credentials）
        var scopes = context.getAuthorizedScopes();
        if (scopes != null) {
          if (scopes.contains("admin")) roles.add("ROLE_ADMIN");
          if (scopes.contains("read")) roles.add("ROLE_READER");
          if (scopes.contains("write")) roles.add("ROLE_WRITER");
        }

        if (!roles.isEmpty()) {
          context.getClaims().claim("roles", roles);
        }

        // 补充用户 profile 信息（email, phone, nickname, totpEnabled）
        try {
          var userOpt = userRepository.findByUsername(username);
          if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getEmail() != null) {
              context.getClaims().claim("email", user.getEmail());
            }
            if (user.getPhone() != null) {
              context.getClaims().claim("phone", user.getPhone());
            }
            if (user.getNickname() != null) {
              context.getClaims().claim("nickname", user.getNickname());
              context.getClaims().claim("preferred_username", user.getNickname());
            }
            if (user.getAvatar() != null) {
              context.getClaims().claim("picture", user.getAvatar());
            }
            context.getClaims().claim("mfa_enabled", user.getTotpEnabled());
          }
        } catch (Exception ignored) {
          // client_credentials 模式下 username 是 client_id，无对应 User 记录
        }
      }
    };
  }

  // ==================== 通用 Bean ====================

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder()
        .issuer("http://localhost:9100")
        .build();
  }

}
