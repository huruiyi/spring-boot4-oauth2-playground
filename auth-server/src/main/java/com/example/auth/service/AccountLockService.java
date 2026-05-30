package com.example.auth.service;

import com.example.auth.entity.User;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final UserRepository userRepository;

    @Value("${auth.lock.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${auth.lock.auto-unlock-minutes:30}")
    private long autoUnlockMinutes;

    @Transactional
    public void recordFailedAttempt(String username) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();

        if (user.getRoles() != null && user.getRoles().contains("ROLE_ADMIN")) {
            log.warn("管理员 {} 登录失败，豁免锁定（仅记录失败次数）", username);
            return;
        }

        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            user.setAccountNonLocked(false);
            user.setLockedAt(Instant.now());
            log.warn("用户 {} 已锁定（连续失败 {} 次）", username, attempts);
        } else {
            log.info("用户 {} 登录失败（{}/{}次）", username, attempts, maxFailedAttempts);
        }

        userRepository.save(user);
    }

    @Transactional
    public void resetFailedAttempts(String username) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        if (user.getFailedAttempts() > 0 || !user.getAccountNonLocked()) {
            user.setFailedAttempts(0);
            user.setAccountNonLocked(true);
            user.setLockedAt(null);
            userRepository.save(user);
            log.info("用户 {} 登录成功，重置失败计数并解锁", username);
        }
    }

    @Transactional
    public void unlock(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        user.setAccountNonLocked(true);
        user.setFailedAttempts(0);
        user.setLockedAt(null);
        userRepository.save(user);
        log.info("管理员手动解锁用户: {}", user.getUsername());
    }

    public boolean isAccountLocked(User user) {
        if (user.getAccountNonLocked()) return false;

        if (autoUnlockMinutes > 0 && user.getLockedAt() != null) {
            Instant unlockTime = user.getLockedAt().plusSeconds(autoUnlockMinutes * 60);
            if (Instant.now().isAfter(unlockTime)) {
                user.setAccountNonLocked(true);
                user.setFailedAttempts(0);
                user.setLockedAt(null);
                userRepository.save(user);
                log.info("用户 {} 自动解锁（锁定超过 {} 分钟）", user.getUsername(), autoUnlockMinutes);
                return false;
            }
        }

        return true;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public long getAutoUnlockMinutes() {
        return autoUnlockMinutes;
    }
}