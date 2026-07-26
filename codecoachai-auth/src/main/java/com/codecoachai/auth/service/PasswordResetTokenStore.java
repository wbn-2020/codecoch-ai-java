package com.codecoachai.auth.service;

import com.codecoachai.common.core.util.TextFingerprintUtils;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetTokenStore {

    private static final String TOKEN_KEY_PREFIX = "auth:password-reset:";
    private static final String LOCK_KEY_PREFIX = "auth:password-reset-lock:";

    private final StringRedisTemplate stringRedisTemplate;

    public void issue(String token, Long userId, Duration ttl) {
        stringRedisTemplate.opsForValue().set(tokenKey(token), String.valueOf(userId), ttl);
    }

    public String findUserId(String token) {
        return stringRedisTemplate.opsForValue().get(tokenKey(token));
    }

    /**
     * Redis GETDEL is atomic, so the reset token cannot be consumed twice.
     */
    public String consume(String token) {
        return stringRedisTemplate.opsForValue().getAndDelete(tokenKey(token));
    }

    public Boolean delete(String token) {
        return stringRedisTemplate.delete(tokenKey(token));
    }

    public String lockKey(String token) {
        return LOCK_KEY_PREFIX + TextFingerprintUtils.sha256Hex(token);
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }
}
