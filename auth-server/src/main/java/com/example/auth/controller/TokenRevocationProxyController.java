package com.example.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TokenRevocationProxyController {

  private final OAuth2AuthorizationService authorizationService;

  public TokenRevocationProxyController(OAuth2AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  @PostMapping("/api/revoke")
  public ResponseEntity<Void> revoke(@RequestBody Map<String, String> body) {
    String token = body.get("token");
    String tokenTypeHint = body.get("token_type_hint");

    if (token == null || token.isBlank()) {
      return ResponseEntity.badRequest().build();
    }

    OAuth2TokenType tokenType = (tokenTypeHint != null) ? new OAuth2TokenType(tokenTypeHint) : null;
    OAuth2Authorization authorization = this.authorizationService.findByToken(token, tokenType);

    if (authorization != null) {
      OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);

      OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
      if (accessToken != null && token.equals(accessToken.getToken().getTokenValue())) {
        builder.token(accessToken.getToken(),
            meta -> meta.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));
      }

      OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
      if (refreshToken != null && token.equals(refreshToken.getToken().getTokenValue())) {
        builder.token(refreshToken.getToken(),
            meta -> meta.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true));
      }

      this.authorizationService.save(builder.build());
    }

    return ResponseEntity.ok().build();
  }
}
