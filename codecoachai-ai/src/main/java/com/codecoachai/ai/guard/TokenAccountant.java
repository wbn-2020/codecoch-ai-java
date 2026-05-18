package com.codecoachai.ai.guard;

import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.redis.constant.RedisKeyConstants;
import java.time.Duration;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 用量配额检查 + Token 计费记账。
 *
 * 运行时用 Redis 计数（INCR + TTL）做快速判断；DB 的 ai_quota 表可由独立调度任务定时回写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAccountant {

    private final AiRouterProperties routerProperties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 检查用户配额，超额抛 BusinessException。
     */
    public void checkQuota(Long userId) {
        if (userId == null) {
            return;
        }
        AiRouterProperties.Quota quota = routerProperties.getQuota();
        if (quota == null || !Boolean.TRUE.equals(quota.getEnabled())) {
            return;
        }

        // 每分钟
        if (quota.getPerUserMinute() != null && quota.getPerUserMinute() > 0) {
            String mKey = RedisKeyConstants.aiQuotaMinuteKey(userId);
            Long mCnt = redisTemplate.opsForValue().increment(mKey);
            if (mCnt != null && mCnt == 1L) {
                redisTemplate.expire(mKey, Duration.ofMinutes(1));
            }
            if (mCnt != null && mCnt > quota.getPerUserMinute()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "AI 调用过于频繁，请稍后再试（每分钟最多 " + quota.getPerUserMinute() + " 次）");
            }
        }

        // 每天
        if (quota.getPerUserDay() != null && quota.getPerUserDay() > 0) {
            String dKey = RedisKeyConstants.aiQuotaDayKey(userId, LocalDate.now().toString());
            Long dCnt = redisTemplate.opsForValue().increment(dKey);
            if (dCnt != null && dCnt == 1L) {
                redisTemplate.expire(dKey, Duration.ofDays(2));
            }
            if (dCnt != null && dCnt > quota.getPerUserDay()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "今日 AI 用量已达上限（" + quota.getPerUserDay() + " 次），明日重置");
            }
        }
    }

    /**
     * 调用成功后记录 token 消耗。
     * 当前实现仅累加 Redis 计数，便于配额检查；DB 持久化由 ai_call_log 承担。
     */
    public void accumulate(Long userId, int inputTokens, int outputTokens, double cost) {
        if (userId == null) {
            return;
        }
        String inputKey = "codecoachai:ai:tokens:in:" + userId + ":" + LocalDate.now();
        String outputKey = "codecoachai:ai:tokens:out:" + userId + ":" + LocalDate.now();
        if (inputTokens > 0) {
            redisTemplate.opsForValue().increment(inputKey, inputTokens);
            redisTemplate.expire(inputKey, Duration.ofDays(2));
        }
        if (outputTokens > 0) {
            redisTemplate.opsForValue().increment(outputKey, outputTokens);
            redisTemplate.expire(outputKey, Duration.ofDays(2));
        }
        if (cost > 0) {
            log.debug("AI cost userId={} in={} out={} cost={}", userId, inputTokens, outputTokens, cost);
        }
    }

    /**
     * 调用失败时回退分钟级计数（避免被失败误算配额）。
     */
    public void rollbackMinuteCount(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForValue().decrement(RedisKeyConstants.aiQuotaMinuteKey(userId));
    }
}
