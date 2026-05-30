package com.example.auth.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

@Configuration
public class RegisteredClientConfig {

    @Bean
    @Primary
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate, 
                                                                PasswordEncoder passwordEncoder) {
        JdbcRegisteredClientRepository registeredClientRepository = new JdbcRegisteredClientRepository(jdbcTemplate);

        deleteClientIfExists(registeredClientRepository, jdbcTemplate, "oidc-client");
        deleteClientIfExists(registeredClientRepository, jdbcTemplate, "resource-server");
        deleteClientIfExists(registeredClientRepository, jdbcTemplate, "spa-client");
        deleteClientIfExists(registeredClientRepository, jdbcTemplate, "spa-client-vue3");

        RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("oidc-client")
            .clientSecret(passwordEncoder.encode("secret"))
            .clientName("Web Client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("http://localhost:8100/login/oauth2/code/my-client")
            .redirectUri("http://127.0.0.1:8100/login/oauth2/code/my-client")
            .redirectUri("http://localhost:8100/")
            .redirectUri("http://127.0.0.1:8100/")
            .postLogoutRedirectUri("http://localhost:8100/")
            .postLogoutRedirectUri("http://127.0.0.1:8100/")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("read")
            .scope("write")
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .requireProofKey(true)
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(3))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)
                .build())
            .build();
        registeredClientRepository.save(webClient);

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
                .accessTokenTimeToLive(Duration.ofMinutes(3))
                .build())
            .build();
        registeredClientRepository.save(resourceServerClient);

        RegisteredClient spaClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("spa-client")
            .clientName("SPA Client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:3100/callback.html")
            .redirectUri("http://127.0.0.1:3100/callback.html")
            .redirectUri("http://localhost:3100/silent-refresh.html")
            .redirectUri("http://127.0.0.1:3100/silent-refresh.html")
            .postLogoutRedirectUri("http://localhost:3100/")
            .postLogoutRedirectUri("http://127.0.0.1:3100/")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("read")
            .scope("write")
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .requireProofKey(true)
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(3))
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
            .redirectUri("http://localhost:3200/callback")
            .redirectUri("http://127.0.0.1:3200/callback")
            .redirectUri("http://localhost:3200/silent-refresh.html")
            .redirectUri("http://127.0.0.1:3200/silent-refresh.html")
            .redirectUri("http://localhost:4173/callback")
            .redirectUri("http://127.0.0.1:4173/callback")
            .redirectUri("http://localhost:4173/silent-refresh.html")
            .redirectUri("http://127.0.0.1:4173/silent-refresh.html")
            .postLogoutRedirectUri("http://localhost:3200/")
            .postLogoutRedirectUri("http://127.0.0.1:3200/")
            .postLogoutRedirectUri("http://localhost:4173/")
            .postLogoutRedirectUri("http://127.0.0.1:4173/")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("read")
            .scope("write")
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .requireProofKey(true)
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(3))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)
                .build())
            .build();
        registeredClientRepository.save(spaVue3Client);

        return registeredClientRepository;
    }

    private static void deleteClientIfExists(RegisteredClientRepository repo, JdbcTemplate jdbc, String clientId) {
        RegisteredClient existing = repo.findByClientId(clientId);
        if (existing != null) {
            String id = existing.getId();
            jdbc.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", id);
            jdbc.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", id);
            jdbc.update("DELETE FROM oauth2_registered_client WHERE id = ?", id);
        }
    }
}
