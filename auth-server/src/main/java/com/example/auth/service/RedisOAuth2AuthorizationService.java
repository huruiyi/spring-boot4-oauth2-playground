package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String AUTHORIZATION_KEY_PREFIX = "oauth2:authorization:";
    private static final String TOKEN_KEY_PREFIX = "oauth2:token:";
    private static final String REFRESH_KEY_PREFIX = "oauth2:refresh:";
    private static final String CODE_KEY_PREFIX = "oauth2:code:";
    private static final String STATE_KEY_PREFIX = "oauth2:state:";
    private static final long DEFAULT_TIMEOUT_HOURS = 30;

    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");

        long timeout = DEFAULT_TIMEOUT_HOURS;
        TimeUnit timeUnit = TimeUnit.HOURS;

        if (authorization.getRefreshToken() != null && authorization.getRefreshToken().getToken().getExpiresAt() != null) {
            long expiresInSeconds = authorization.getRefreshToken().getToken().getExpiresAt().getEpochSecond()
                    - Instant.now().getEpochSecond();
            if (expiresInSeconds > 0) {
                timeout = expiresInSeconds;
                timeUnit = TimeUnit.SECONDS;
            }
        } else if (authorization.getAccessToken() != null && authorization.getAccessToken().getToken().getExpiresAt() != null) {
            long expiresInSeconds = authorization.getAccessToken().getToken().getExpiresAt().getEpochSecond()
                    - Instant.now().getEpochSecond();
            if (expiresInSeconds > 0) {
                timeout = expiresInSeconds;
                timeUnit = TimeUnit.SECONDS;
            }
        }

        String idKey = AUTHORIZATION_KEY_PREFIX + authorization.getId();
        redisTemplate.opsForValue().set(idKey, serialize(authorization), timeout, timeUnit);

        if (authorization.getAccessToken() != null) {
            String tokenKey = TOKEN_KEY_PREFIX + authorization.getAccessToken().getToken().getTokenValue();
            redisTemplate.opsForValue().set(tokenKey, authorization.getId(), timeout, timeUnit);
        }

        if (authorization.getRefreshToken() != null) {
            String refreshKey = REFRESH_KEY_PREFIX + authorization.getRefreshToken().getToken().getTokenValue();
            redisTemplate.opsForValue().set(refreshKey, authorization.getId(), timeout, timeUnit);
        }

        // authorization_code 索引 (5分钟过期) — 7.0 使用 getToken(Class<T>) 替代 getToken(OAuth2TokenType)
        var authorizationCodeToken = authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCodeToken != null) {
            String codeKey = CODE_KEY_PREFIX + authorizationCodeToken.getToken().getTokenValue();
            redisTemplate.opsForValue().set(codeKey, authorization.getId(), 5, TimeUnit.MINUTES);
        }

        if (authorization.getAttribute("state") != null) {
            String stateKey = STATE_KEY_PREFIX + authorization.getAttribute("state");
            redisTemplate.opsForValue().set(stateKey, authorization.getId(), 5, TimeUnit.MINUTES);
        }

        log.debug("Saved authorization id={}", authorization.getId());
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");

        String idKey = AUTHORIZATION_KEY_PREFIX + authorization.getId();
        redisTemplate.delete(idKey);

        if (authorization.getAccessToken() != null) {
            String tokenKey = TOKEN_KEY_PREFIX + authorization.getAccessToken().getToken().getTokenValue();
            redisTemplate.delete(tokenKey);
        }

        if (authorization.getRefreshToken() != null) {
            String refreshKey = REFRESH_KEY_PREFIX + authorization.getRefreshToken().getToken().getTokenValue();
            redisTemplate.delete(refreshKey);
        }

        var authorizationCodeToken = authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCodeToken != null) {
            String codeKey = CODE_KEY_PREFIX + authorizationCodeToken.getToken().getTokenValue();
            redisTemplate.delete(codeKey);
        }

        if (authorization.getAttribute("state") != null) {
            String stateKey = STATE_KEY_PREFIX + authorization.getAttribute("state");
            redisTemplate.delete(stateKey);
        }

        log.debug("Removed authorization id={}", authorization.getId());
    }

    @Override
    @Nullable
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        String idKey = AUTHORIZATION_KEY_PREFIX + id;
        return deserialize((String) redisTemplate.opsForValue().get(idKey));
    }

    @Override
    @Nullable
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");

        String id = null;

        if (tokenType == null) {
            id = (String) redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
            if (id == null) {
                id = (String) redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + token);
            }
            if (id == null) {
                id = (String) redisTemplate.opsForValue().get(CODE_KEY_PREFIX + token);
            }
            if (id == null) {
                id = (String) redisTemplate.opsForValue().get(STATE_KEY_PREFIX + token);
            }
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            id = (String) redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            id = (String) redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            id = (String) redisTemplate.opsForValue().get(CODE_KEY_PREFIX + token);
        } else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            id = (String) redisTemplate.opsForValue().get(STATE_KEY_PREFIX + token);
        }

        if (id == null) {
            return null;
        }

        return findById(id);
    }

    private String serialize(OAuth2Authorization authorization) {
        try {
            return objectMapper.writeValueAsString(authorization);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize OAuth2Authorization", e);
        }
    }

    private OAuth2Authorization deserialize(String data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, OAuth2Authorization.class);
        } catch (Exception e) {
            log.error("Failed to deserialize OAuth2Authorization", e);
            return null;
        }
    }
}
