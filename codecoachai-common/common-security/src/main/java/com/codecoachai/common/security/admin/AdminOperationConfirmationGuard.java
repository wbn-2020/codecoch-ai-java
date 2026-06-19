package com.codecoachai.common.security.admin;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import java.time.Duration;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("commonAdminOperationConfirmationGuard")
@RequiredArgsConstructor
public class AdminOperationConfirmationGuard {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final String REDIS_KEY_PREFIX = "codecoachai:admin-confirmed-operation:";
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private final StringRedisTemplate stringRedisTemplate;

    public String requireConfirmed(String operation, Boolean confirm, String reason, String idempotencyKey) {
        return requireConfirmed(operation, confirm, Boolean.FALSE, reason, idempotencyKey);
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
                    "High-risk operation requires confirm=true, dryRun=false, reason, and idempotencyKey.");
        }
        return acquire(operation, cleanIdempotencyKey);
    }

    public void release(String redisKey) {
        if (!StringUtils.hasText(redisKey)) {
            return;
        }
        try {
            stringRedisTemplate.delete(redisKey);
        } catch (RuntimeException ignored) {
            // The operation already failed; keep the original business exception visible to the caller.
        }
    }

    public String cleanReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String value = reason.trim();
        if (value.length() > 300) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "reason must not exceed 300 characters.");
        }
        return value;
    }

    public String cleanIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String value = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "idempotencyKey must be 8-128 letters, digits, dot, underscore, colon, or dash.");
        }
        return value;
    }

    private String acquire(String operation, String idempotencyKey) {
        String safeOperation = StringUtils.hasText(operation) ? operation.trim() : "UNKNOWN";
        String redisKey = REDIS_KEY_PREFIX + safeOperation + ":" + idempotencyKey;
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, "1", DEFAULT_TTL);
            if (Boolean.TRUE.equals(ok)) {
                return redisKey;
            }
        } catch (RedisConnectionFailureException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "High-risk operation idempotency cannot be verified because Redis is unavailable.");
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "High-risk operation idempotency cannot be verified. Please retry later.");
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR,
                "Duplicate high-risk operation request. Use a new idempotencyKey after confirming intent.");
    }
}
