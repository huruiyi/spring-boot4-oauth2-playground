package com.example.auth.handler;

import com.example.auth.service.AccountLockService;
import com.example.auth.service.LoginRateLimitService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final AccountLockService accountLockService;
    private final LoginRateLimitService loginRateLimitService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String clientIp = getClientIp(request);

        loginRateLimitService.recordIpFailure(clientIp);

        if (exception instanceof LockedException) {
            log.warn("用户 {} 尝试登录但账户已锁定（IP: {}）", username, clientIp);
            response.sendRedirect("/login?locked");
            return;
        }

        if (exception instanceof BadCredentialsException || exception instanceof UsernameNotFoundException) {
            if (username != null && !username.isEmpty()) {
                accountLockService.recordFailedAttempt(username);
            }
            log.warn("用户 {} 登录失败（IP: {}）: {}", username, clientIp, exception.getMessage());
        }

        if (loginRateLimitService.isIpBlocked(clientIp)) {
            log.warn("IP {} 已被限速", clientIp);
            response.sendRedirect("/login?ratelimit");
            return;
        }

        response.sendRedirect("/login?error");
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}