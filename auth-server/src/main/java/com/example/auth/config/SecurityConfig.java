package com.example.auth.config;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  private final UserRepository userRepository;
  private RegisteredClientRepository registeredClientRepositoryRef;

  public SecurityConfig(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  // ==================== Security Filter Chains ====================

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
                "default-src 'self'; style-src 'self'; frame-ancestors 'none';"))
        )
        .addFilterBefore((request, response, chain) -> {
            jakarta.servlet.http.HttpServletRequest req = (jakarta.servlet.http.HttpServletRequest) request;
            if (req.getRequestURI().contains("/oauth2/authorize")) {
                log.info("授权请求: method={}, client_id={}, redirect_uri={}, response_type={}, state={}, code_challenge={}",
                    req.getMethod(),
                    req.getParameter("client_id"),
                    req.getParameter("redirect_uri"),
                    req.getParameter("response_type"),
                    req.getParameter("state"),
                    req.getParameter("code_challenge") != null ? "存在" : "不存在");
            }
            chain.doFilter(request, response);
        }, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/login", "/oauth2/consent", "/css/**", "/js/**", "/error").permitAll()
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login")
            .permitAll()
            .successHandler((request, response, authentication) -> {
              log.info("用户 {} 登录成功（IP: {}）", authentication.getName(), getClientIp(request));
              new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler()
                  .onAuthenticationSuccess(request, response, authentication);
            })
            .failureHandler((request, response, exception) -> {
              log.warn("用户登录失败（IP: {}）: {}", getClientIp(request), exception.getMessage());
              response.sendRedirect("/login?error");
            })
        )
        .cors(Customizer.withDefaults())
        .headers(headers -> headers
            .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; style-src 'self'; frame-ancestors 'none';"))
            .frameOptions(frame -> frame.deny())
        );

    return http.build();
  }

  // ==================== CORS ====================

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:3001", "http://127.0.0.1:3001"));
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  // ==================== 客户端存储 ====================

  @Bean
  @Primary
  public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
    JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);

    RegisteredClient existingWebClient = registeredClientRepository.findByClientId("oidc-client");
    if (existingWebClient != null) {
      jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", existingWebClient.getId());
      jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", existingWebClient.getId());
      jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", existingWebClient.getId());
    }
    RegisteredClient existingResourceServerClient = registeredClientRepository.findByClientId("resource-server");
    if (existingResourceServerClient != null) {
      jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", existingResourceServerClient.getId());
      jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", existingResourceServerClient.getId());
      jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", existingResourceServerClient.getId());
    }
    RegisteredClient existingSpaClient = registeredClientRepository.findByClientId("spa-client");
    if (existingSpaClient != null) {
      jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", existingSpaClient.getId());
      jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", existingSpaClient.getId());
      jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", existingSpaClient.getId());
    }
    RegisteredClient existingSpaVue3Client = registeredClientRepository.findByClientId("spa-client-vue3");
    if (existingSpaVue3Client != null) {
      jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", existingSpaVue3Client.getId());
      jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", existingSpaVue3Client.getId());
      jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", existingSpaVue3Client.getId());
    }

    RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("oidc-client")
        .clientSecret(passwordEncoder.encode("secret"))
        .clientName("Web Client")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .redirectUri("http://localhost:8080/login/oauth2/code/my-client")
        .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-client")
        .redirectUri("http://localhost:8080/")
        .redirectUri("http://127.0.0.1:8080/")
        .postLogoutRedirectUri("http://localhost:8080/")
        .postLogoutRedirectUri("http://127.0.0.1:8080/")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope("read")
        .scope("write")
        .clientSettings(ClientSettings.builder()
            .requireAuthorizationConsent(false)
            .requireProofKey(true)
            .build())
        .tokenSettings(TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofHours(1))
            .refreshTokenTimeToLive(Duration.ofDays(30))
            .reuseRefreshTokens(false)
            .build())
        .build();

    RegisteredClient resourceServerClient = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("resource-server")
        .clientSecret(passwordEncoder.encode("secret"))
        .clientName("Resource Server")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("read")
        .scope("write")
        .scope("admin")
        .clientSettings(ClientSettings.builder()
            .requireAuthorizationConsent(false)
            .build())
        .tokenSettings(TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofHours(1))
            .build())
        .build();

    registeredClientRepository.save(webClient);
    registeredClientRepository.save(resourceServerClient);

    RegisteredClient spaClient = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("spa-client")
        .clientName("SPA Client")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://localhost:3000/callback.html")
        .redirectUri("http://127.0.0.1:3000/callback.html")
        .postLogoutRedirectUri("http://localhost:3000/")
        .postLogoutRedirectUri("http://127.0.0.1:3000/")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope("read")
        .scope("write")
        .clientSettings(ClientSettings.builder()
            .requireAuthorizationConsent(false)
            .requireProofKey(true)
            .build())
        .tokenSettings(TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofHours(1))
            .refreshTokenTimeToLive(Duration.ofDays(30))
            .reuseRefreshTokens(false)
            .build())
        .build();

    registeredClientRepository.save(spaClient);

    RegisteredClient spaVue3Client = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("spa-client-vue3")
        .clientName("SPA Client Vue3")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://localhost:3001/callback")
        .redirectUri("http://127.0.0.1:3001/callback")
        .postLogoutRedirectUri("http://localhost:3001/")
        .postLogoutRedirectUri("http://127.0.0.1:3001/")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .scope("read")
        .scope("write")
        .clientSettings(ClientSettings.builder()
            .requireAuthorizationConsent(false)
            .requireProofKey(true)
            .build())
        .tokenSettings(TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofHours(1))
            .refreshTokenTimeToLive(Duration.ofDays(30))
            .reuseRefreshTokens(false)
            .build())
        .build();

    registeredClientRepository.save(spaVue3Client);

    this.registeredClientRepositoryRef = registeredClientRepository;

    return registeredClientRepository;
  }

  @Bean
  public org.springframework.boot.CommandLineRunner clientDiagnostic(JdbcTemplate jdbcTemplate, RegisteredClientRepository repo) {
    return args -> {
      log.info("========== 客户端注册诊断 ==========");
      java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT client_id, client_name, client_authentication_methods, redirect_uris FROM oauth2_registered_client");
      for (var row : rows) {
        log.info("DB 客户端: {}", row);
      }
      for (String cid : java.util.List.of("oidc-client", "spa-client", "spa-client-vue3", "resource-server")) {
        RegisteredClient c = repo.findByClientId(cid);
        log.info("Repository 查找 '{}': {}", cid, c != null ? "找到 - authMethods=" + c.getClientAuthenticationMethods() + ", redirectUris=" + c.getRedirectUris() : "未找到!");
      }
      log.info("====================================");
    };
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
  public JWKSource<SecurityContext> jwkSource() {
    KeyPair keyPair = generateRsaKey();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
    RSAKey rsaKey = new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
  }

  private static KeyPair generateRsaKey() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      return keyPairGenerator.generateKeyPair();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to generate RSA key pair", ex);
    }
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

        // 补充用户 profile 信息（email, phone, nickname）
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
        .issuer("http://localhost:9000")
        .build();
  }

  private static String getClientIp(HttpServletRequest request) {
    String xf = request.getHeader("X-Forwarded-For");
    if (xf != null) return xf.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
