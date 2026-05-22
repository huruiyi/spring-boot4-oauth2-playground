package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CONSENT_KEY_PREFIX = "consent:";
    private static final long CONSENT_TIMEOUT_DAYS = 30;

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        String key = buildKey(authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
        redisTemplate.opsForValue().set(key, authorizationConsent, CONSENT_TIMEOUT_DAYS, TimeUnit.DAYS);
        log.debug("Saved consent for clientId={}, principal={}",
                authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        String key = buildKey(authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
        redisTemplate.delete(key);
        log.debug("Removed consent for clientId={}, principal={}",
                authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName());
    }

    @Override
    @Nullable
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        Assert.hasText(registeredClientId, "registeredClientId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");
        String key = buildKey(registeredClientId, principalName);
        return (OAuth2AuthorizationConsent) redisTemplate.opsForValue().get(key);
    }

    private String buildKey(String registeredClientId, String principalName) {
        return CONSENT_KEY_PREFIX + principalName + ":" + registeredClientId;
    }
}
