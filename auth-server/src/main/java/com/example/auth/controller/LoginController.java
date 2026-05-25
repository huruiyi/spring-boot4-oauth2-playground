package com.example.auth.controller;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.MfaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class LoginController {

    private final UserRepository userRepository;
    private final MfaService mfaService;

    public LoginController(UserRepository userRepository, MfaService mfaService) {
        this.userRepository = userRepository;
        this.mfaService = mfaService;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails != null) {
            User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
            if (user != null) {
                model.addAttribute("username", user.getUsername());
                model.addAttribute("nickname", user.getNickname());
                model.addAttribute("email", user.getEmail());
                model.addAttribute("roles", user.getRoles());
                model.addAttribute("totpEnabled", user.getTotpEnabled());
                
                List<String> recoveryCodes = mfaService.deserializeRecoveryCodes(user.getRecoveryCodes());
                model.addAttribute("recoveryCodesCount", recoveryCodes.size());
            }
        }
        model.addAttribute("clients", List.of("oidc-client", "spa-client", "spa-client-vue3", "resource-server"));
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
