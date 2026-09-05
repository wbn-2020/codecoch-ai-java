package com.codecoachai.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenStoreTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordResetTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new PasswordResetTokenStore(stringRedisTemplate);
    }

    @Test
    void consumeDelegatesToAtomicGetAndDelete() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:password-reset:reset-token")).thenReturn("42");

        String userId = tokenStore.consume("reset-token");

        assertThat(userId).isEqualTo("42");
        verify(valueOperations).getAndDelete("auth:password-reset:reset-token");
        verify(valueOperations, never()).get("auth:password-reset:reset-token");
        verify(stringRedisTemplate, never()).delete("auth:password-reset:reset-token");
    }

    @Test
    void lockKeyIsDeterministicAndDoesNotExposeRawToken() {
        String first = tokenStore.lockKey("sensitive-reset-token");
        String second = tokenStore.lockKey("sensitive-reset-token");

        assertThat(first)
                .isEqualTo(second)
                .startsWith("auth:password-reset-lock:")
                .doesNotContain("sensitive-reset-token");
    }

    @Test
    void issueStoresUserIdWithRequestedTtl() {
        Duration ttl = Duration.ofMinutes(15);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenStore.issue("reset-token", 42L, ttl);

        verify(valueOperations).set("auth:password-reset:reset-token", "42", ttl);
    }
}
