package com.codecoachai.ai.agent.security;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class AdminOperationConfirmationGuard {

    private static final long IDEMPOTENCY_TTL_MILLIS = 30 * 60 * 1000L;
    private static final String REDIS_KEY_PREFIX = "codecoachai:admin-confirmed-operation:";
    private final StringRedisTemplate redisTemplate;

    public AdminOperationConfirmationGuard(StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = stringRedisTemplate;
    }

    public String requireConfirmed(String operation, Boolean confirm, Boolean dryRun,
                                          String reason, String idempotencyKey) {
        String cleanReason = cleanReason(reason);
        String cleanIdempotencyKey = cleanIdempotencyKey(idempotencyKey);
        if (Boolean.TRUE.equals(dryRun)
                || !Boolean.TRUE.equals(confirm)
                || !StringUtils.hasText(cleanReason)
                || !StringUtils.hasText(cleanIdempotencyKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该高风险操作需要 confirm=true、dryRun=false、reason 和 idempotencyKey。");
        }
        return acquire(operation, cleanIdempotencyKey);
    }

    public void release(String lockKey) {
        if (StringUtils.hasText(lockKey)) {
            if (redisTemplate != null) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    private String cleanReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String value = reason.trim();
        if (value.length() > 300) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "操作原因不能超过 300 个字符");
        }
        return value;
    }

    private String cleanIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String value = idempotencyKey.trim();
        if (value.length() < 8 || value.length() > 128 || !value.matches("[A-Za-z0-9:_-]+")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "幂等键格式不正确，请使用 8-128 位字母、数字、横线、下划线或冒号");
        }
        return value;
    }

    private String acquire(String operation, String idempotencyKey) {
        String safeOperation = StringUtils.hasText(operation) ? operation.trim() : "UNKNOWN";
        String lockKey = REDIS_KEY_PREFIX + safeOperation + ":" + idempotencyKey;
        if (redisTemplate == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "High-risk operation idempotency requires Redis and is not available.");
        }
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofMillis(IDEMPOTENCY_TTL_MILLIS));
            if (Boolean.TRUE.equals(ok)) {
                return lockKey;
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "High-risk operation idempotency cannot be verified. Please retry after Redis is available.");
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "重复的高风险操作请求已被拦截，请更换幂等键或稍后重试");
    }

}
