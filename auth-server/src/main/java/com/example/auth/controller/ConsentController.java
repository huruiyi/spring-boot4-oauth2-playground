package com.example.auth.controller;

import java.security.Principal;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ConsentController {

  private final RegisteredClientRepository registeredClientRepository;

  private final OAuth2AuthorizationConsentService consentService;

  @GetMapping("/oauth2/consent")
  public String consent(
      @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
      @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
      @RequestParam(OAuth2ParameterNames.STATE) String state,
      @RequestParam(value = OAuth2ParameterNames.USER_CODE, required = false) String userCode,
      Principal principal,
      Model model) {

    RegisteredClient client = registeredClientRepository.findByClientId(clientId);

    Set<String> requestedScopes = StringUtils.commaDelimitedListToSet(scope);
    Set<String> previouslyApprovedScopes = getPreviouslyApprovedScopes(clientId, principal.getName());
    Set<String> newScopes = requestedScopes.stream()
        .filter(s -> !previouslyApprovedScopes.contains(s))
        .collect(Collectors.toSet());

    model.addAttribute("clientId", clientId);
    model.addAttribute("clientName", client != null ? client.getClientName() : clientId);
    model.addAttribute("state", state);
    model.addAttribute("scopes", requestedScopes);
    model.addAttribute("newScopes", newScopes);
    model.addAttribute("previouslyApprovedScopes", previouslyApprovedScopes);
    model.addAttribute("principalName", principal.getName());
    model.addAttribute("redirectUri", client != null && client.getRedirectUris() != null
        ? client.getRedirectUris().iterator().next()
        : "/");
    model.addAttribute("userCode", userCode);

    if (requestedScopes.isEmpty()) {
      return "redirect:/oauth2/authorize?" +
          "client_id=" + clientId +
          "&scope=" + scope +
          "&state=" + state +
          "&consent_approved=true";
    }

    return "consent";
  }

  private Set<String> getPreviouslyApprovedScopes(String clientId, String principalName) {
    OAuth2AuthorizationConsent consent = consentService.findById(clientId, principalName);
    return consent != null ? consent.getScopes() : Set.of();
  }
}
