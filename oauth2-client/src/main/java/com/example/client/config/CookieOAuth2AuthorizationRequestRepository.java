package com.example.client.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * 基于 Cookie 的 OAuth2AuthorizationRequest 存储实现。
 * 解决 session 在 OAuth2 重定向回调时丢失导致 authorization_request_not_found 的问题。
 */
public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);
    private static final String AUTH_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 300;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String cookieValue = getCookieValue(request, AUTH_REQUEST_COOKIE_NAME);
        if (!StringUtils.hasText(cookieValue)) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cookieValue), StandardCharsets.UTF_8);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return reconstructAuthorizationRequest(map);
        } catch (Exception e) {
            log.error("从 Cookie 反序列化 OAuth2AuthorizationRequest 失败", e);
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response, AUTH_REQUEST_COOKIE_NAME);
            return;
        }
        try {
            Map<String, Object> map = flattenAuthorizationRequest(authorizationRequest);
            String json = objectMapper.writeValueAsString(map);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            addCookie(response, AUTH_REQUEST_COOKIE_NAME, encoded, COOKIE_EXPIRE_SECONDS);
        } catch (Exception e) {
            log.error("序列化 OAuth2AuthorizationRequest 到 Cookie 失败", e);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
        deleteCookie(response, AUTH_REQUEST_COOKIE_NAME);
        return authRequest;
    }

    // ==================== 序列化辅助 ====================

    private Map<String, Object> flattenAuthorizationRequest(OAuth2AuthorizationRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("authorizationUri", request.getAuthorizationUri());
        map.put("grantType", request.getGrantType().getValue());
        map.put("clientId", request.getClientId());
        map.put("redirectUri", request.getRedirectUri());
        map.put("scopes", request.getScopes());
        map.put("state", request.getState());
        map.put("additionalParameters", request.getAdditionalParameters());
        map.put("authorizationRequestUri", request.getAuthorizationRequestUri());
        map.put("attributes", request.getAttributes());
        return map;
    }

    @SuppressWarnings("unchecked")
    private OAuth2AuthorizationRequest reconstructAuthorizationRequest(Map<String, Object> map) {
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode();
        builder.authorizationUri((String) map.get("authorizationUri"));
        builder.clientId((String) map.get("clientId"));
        builder.redirectUri((String) map.get("redirectUri"));
        builder.state((String) map.get("state"));

        Object scopes = map.get("scopes");
        if (scopes instanceof java.util.Collection) {
            builder.scopes(new LinkedHashSet<>((java.util.Collection<String>) scopes));
        }

        Object attributes = map.get("attributes");
        if (attributes instanceof Map) {
            builder.attributes(attrs -> attrs.putAll((Map<String, Object>) attributes));
        }

        Object additionalParams = map.get("additionalParameters");
        if (additionalParams instanceof Map) {
            builder.additionalParameters(params -> params.putAll((Map<String, Object>) additionalParams));
        }

        return builder.build();
    }

    // ==================== Cookie 辅助 ====================

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    private void deleteCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
