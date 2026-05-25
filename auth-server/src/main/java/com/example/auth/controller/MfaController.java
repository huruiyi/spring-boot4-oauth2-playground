package com.example.auth.controller;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.MfaService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final UserRepository userRepository;

    private static final String MFA_VERIFIED = "MFA_VERIFIED";
    private static final String ISSUER = "Spring-Boot4-OAuth2";

    @GetMapping("/setup")
    public String setup(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if (user.getTotpEnabled()) {
            model.addAttribute("totpEnabled", true);
            
            List<String> recoveryCodes = mfaService.deserializeRecoveryCodes(user.getRecoveryCodes());
            model.addAttribute("recoveryCodes", recoveryCodes);
            model.addAttribute("recoveryCodesCount", recoveryCodes.size());
            
            return "mfa-setup";
        }
        
        String secret = mfaService.generateSecret();
        String qrCodeBase64 = mfaService.generateQrCodeBase64(user.getUsername(), secret, ISSUER);
        
        model.addAttribute("secret", secret);
        model.addAttribute("qrCode", qrCodeBase64);
        model.addAttribute("totpEnabled", false);
        
        return "mfa-setup";
    }

    @PostMapping("/enable")
    public String enable(@AuthenticationPrincipal UserDetails userDetails,
                         @RequestParam String secret,
                         @RequestParam String code,
                         Model model) {
        if (!mfaService.isCodeValid(secret, code)) {
            model.addAttribute("error", "验证码无效，请重新输入");
            String qrCodeBase64 = mfaService.generateQrCodeBase64(userDetails.getUsername(), secret, ISSUER);
            model.addAttribute("secret", secret);
            model.addAttribute("qrCode", qrCodeBase64);
            model.addAttribute("totpEnabled", false);
            return "mfa-setup";
        }
        
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        List<String> recoveryCodes = mfaService.generateRecoveryCodes();
        String recoveryCodesJson = mfaService.serializeRecoveryCodes(recoveryCodes);
        
        user.setTotpSecret(secret);
        user.setTotpEnabled(true);
        user.setRecoveryCodes(recoveryCodesJson);
        userRepository.save(user);
        
        log.info("用户 {} 已启用 MFA (TOTP)，生成 {} 个恢复码", userDetails.getUsername(), recoveryCodes.size());
        
        model.addAttribute("recoveryCodes", recoveryCodes);
        model.addAttribute("totpEnabled", true);
        model.addAttribute("showRecoveryCodes", true);
        
        return "mfa-setup";
    }

    @PostMapping("/disable")
    public String disable(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        user.setRecoveryCodes(null);
        userRepository.save(user);
        
        log.info("用户 {} 已禁用 MFA (TOTP)", userDetails.getUsername());
        
        return "redirect:/mfa/setup";
    }
    
    @PostMapping("/regenerate-codes")
    public String regenerateCodes(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if (!user.getTotpEnabled()) {
            return "redirect:/mfa/setup";
        }
        
        List<String> recoveryCodes = mfaService.generateRecoveryCodes();
        String recoveryCodesJson = mfaService.serializeRecoveryCodes(recoveryCodes);
        
        user.setRecoveryCodes(recoveryCodesJson);
        userRepository.save(user);
        
        log.info("用户 {} 重新生成恢复码", userDetails.getUsername());
        
        model.addAttribute("recoveryCodes", recoveryCodes);
        model.addAttribute("totpEnabled", true);
        model.addAttribute("showRecoveryCodes", true);
        
        return "mfa-setup";
    }

    @GetMapping("/verify")
    public String verifyForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if (!user.getTotpEnabled()) {
            return "redirect:/";
        }
        
        List<String> recoveryCodes = mfaService.deserializeRecoveryCodes(user.getRecoveryCodes());
        model.addAttribute("hasRecoveryCodes", !recoveryCodes.isEmpty());
        
        return "mfa-verify";
    }

    @PostMapping("/verify")
    public String verify(@AuthenticationPrincipal UserDetails userDetails,
                         @RequestParam String code,
                         HttpSession session,
                         Model model) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if (!user.getTotpEnabled()) {
            return "redirect:/";
        }
        
        boolean totpValid = mfaService.isCodeValid(user.getTotpSecret(), code);
        
        String updatedRecoveryCodes = null;
        boolean recoveryCodeUsed = false;
        
        if (!totpValid) {
            updatedRecoveryCodes = mfaService.consumeRecoveryCode(user.getRecoveryCodes(), code);
            if (updatedRecoveryCodes != null) {
                recoveryCodeUsed = true;
                user.setRecoveryCodes(updatedRecoveryCodes);
                userRepository.save(user);
                log.info("用户 {} 使用恢复码验证成功", userDetails.getUsername());
            }
        }
        
        if (!totpValid && !recoveryCodeUsed) {
            model.addAttribute("error", "验证码或恢复码无效");
            List<String> recoveryCodes = mfaService.deserializeRecoveryCodes(user.getRecoveryCodes());
            model.addAttribute("hasRecoveryCodes", !recoveryCodes.isEmpty());
            return "mfa-verify";
        }
        
        session.setAttribute(MFA_VERIFIED, true);
        session.removeAttribute("MFA_REQUIRED");
        log.info("用户 {} MFA 验证成功 (方式: {})", userDetails.getUsername(), 
            recoveryCodeUsed ? "恢复码" : "TOTP");
        
        String savedRequest = (String) session.getAttribute("SAVED_REQUEST");
        if (savedRequest != null && !savedRequest.isEmpty()) {
            session.removeAttribute("SAVED_REQUEST");
            return "redirect:" + savedRequest;
        }
        
        return "redirect:/";
    }
}
