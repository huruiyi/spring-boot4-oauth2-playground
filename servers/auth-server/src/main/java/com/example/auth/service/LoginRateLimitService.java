package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String IP_PREFIX = "login:ratelimit:ip:";
    private static final int MAX_ATTEMPTS_PER_IP = 20;
    private static final long WINDOW_SECONDS = 300;

    public boolean isIpBlocked(String ip) {
        String key = IP_PREFIX + ip;
        String count = redisTemplate.opsForValue().get(key);
        if (count != null && Integer.parseInt(count) >= MAX_ATTEMPTS_PER_IP) {
            log.warn("IP {} 登录尝试次数超限（{}次/{}秒）", ip, MAX_ATTEMPTS_PER_IP, WINDOW_SECONDS);
            return true;
        }
        return false;
    }

    public void recordIpFailure(String ip) {
        String key = IP_PREFIX + ip;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void resetIpAttempts(String ip) {
        redisTemplate.delete(IP_PREFIX + ip);
    }

    public int getRemainingIpAttempts(String ip) {
        String key = IP_PREFIX + ip;
        String count = redisTemplate.opsForValue().get(key);
        if (count == null) return MAX_ATTEMPTS_PER_IP;
        return Math.max(0, MAX_ATTEMPTS_PER_IP - Integer.parseInt(count));
    }
}