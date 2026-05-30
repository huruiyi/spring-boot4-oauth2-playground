package com.example.auth.handler;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountLockService;
import com.example.auth.service.LoginRateLimitService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class MfaAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final AccountLockService accountLockService;
    private final LoginRateLimitService loginRateLimitService;
    
    private static final String MFA_REQUIRED = "MFA_REQUIRED";
    private static final String SAVED_REQUEST = "SAVED_REQUEST";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, 
                                        Authentication authentication) throws ServletException, IOException {
        
        String username = authentication.getName();
        String clientIp = getClientIp(request);
        log.info("用户 {} 登录成功（IP: {}）", username, clientIp);
        
        accountLockService.resetFailedAttempts(username);
        loginRateLimitService.resetIpAttempts(clientIp);
        
        var userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isPresent() && userOpt.get().getTotpEnabled()) {
            handleMfaRequired(request, response, authentication);
            return;
        }
        
        super.onAuthenticationSuccess(request, response, authentication);
    }
    
    private void handleMfaRequired(HttpServletRequest request, HttpServletResponse response, 
                                   Authentication authentication) throws IOException {
        
        HttpSession session = request.getSession(true);
        
        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, response);
        if (savedRequest != null) {
            String redirectUrl = savedRequest.getRedirectUrl();
            session.setAttribute(SAVED_REQUEST, redirectUrl);
            log.debug("保存原始请求: {}", redirectUrl);
        }
        
        session.setAttribute(MFA_REQUIRED, true);
        log.debug("用户 {} 已启用 MFA，重定向到 /mfa/verify", authentication.getName());
        response.sendRedirect("/mfa/verify");
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
