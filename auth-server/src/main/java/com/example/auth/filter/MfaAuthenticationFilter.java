package com.example.auth.filter;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class MfaAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    
    private static final String MFA_VERIFIED = "MFA_VERIFIED";
    private static final String MFA_REQUIRED = "MFA_REQUIRED";
    
    private static final List<String> MFA_WHITELIST = Arrays.asList(
        "/mfa/verify",
        "/mfa/setup",
        "/mfa/enable",
        "/mfa/disable",
        "/login",
        "/logout",
        "/css/",
        "/js/",
        "/error"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        
        if (isWhitelisted(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            !(authentication.getPrincipal() instanceof UserDetails)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (session.getAttribute(MFA_VERIFIED) != null && (Boolean) session.getAttribute(MFA_VERIFIED)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        if (session.getAttribute(MFA_REQUIRED) == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        
        if (user == null || !user.getTotpEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("用户 {} 需要进行 MFA 验证，重定向到 /mfa/verify", userDetails.getUsername());
        response.sendRedirect("/mfa/verify");
    }
    
    private boolean isWhitelisted(String uri) {
        return MFA_WHITELIST.stream().anyMatch(uri::startsWith);
    }
}
