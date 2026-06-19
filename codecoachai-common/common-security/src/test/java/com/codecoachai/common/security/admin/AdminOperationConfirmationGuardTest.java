package com.codecoachai.common.security.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.common.core.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AdminOperationConfirmationGuardTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminOperationConfirmationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AdminOperationConfirmationGuard(stringRedisTemplate);
    }

    @Test
    void requireConfirmedRejectsDryRunAndMissingFieldsBeforeRedis() {
        assertThrows(BusinessException.class,
                () -> guard.requireConfirmed("VECTOR_REBUILD", true, true, "确认重建", "op-12345678"));
        assertThrows(BusinessException.class,
                () -> guard.requireConfirmed("VECTOR_REBUILD", true, false, "", "op-12345678"));
        assertThrows(BusinessException.class,
                () -> guard.requireConfirmed("VECTOR_REBUILD", true, false, "确认重建", "bad key"));
    }

    @Test
    void requireConfirmedAcquiresRedisIdempotencyKey() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:VECTOR_REBUILD:op-12345678"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(true);

        String key = guard.requireConfirmed("VECTOR_REBUILD", true, false, "确认重建", "op-12345678");

        assertEquals("codecoachai:admin-confirmed-operation:VECTOR_REBUILD:op-12345678", key);
    }

    @Test
    void requireConfirmedRejectsDuplicateIdempotencyKey() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:VECTOR_REBUILD:op-12345678"),
                eq("1"),
                eq(Duration.ofMinutes(30)))).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> guard.requireConfirmed("VECTOR_REBUILD", true, false, "确认重建", "op-12345678"));
    }

    @Test
    void requireConfirmedFailsClosedWhenRedisUnavailable() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq("codecoachai:admin-confirmed-operation:VECTOR_REBUILD:op-12345678"),
                eq("1"),
                eq(Duration.ofMinutes(30))))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThrows(BusinessException.class,
                () -> guard.requireConfirmed("VECTOR_REBUILD", true, false, "确认重建", "op-12345678"));
    }

    @Test
    void releaseDeletesExistingLockKey() {
        guard.release("codecoachai:admin-confirmed-operation:VECTOR_REBUILD:op-12345678");

        verify(stringRedisTemplate).delete("codecoachai:admin-confirmed-operation:VECTOR_REBUILD:op-12345678");
    }
}
