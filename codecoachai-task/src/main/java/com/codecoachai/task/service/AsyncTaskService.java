package com.codecoachai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.redis.constant.RedisKeyConstants;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 异步任务追踪服务：在 MQ 消费链路中复用，统一处理：
 * 1. 幂等检查（Redis SETNX）
 * 2. AsyncTask 落库（PENDING -> RUNNING -> SUCCESS/FAILED）
 * 3. 死信入表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskService {

    private final AsyncTaskMapper asyncTaskMapper;
    private final MessageDeadLetterMapper deadLetterMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 幂等检查并初始化任务记录。
     *
     * @return true=首次处理（请继续）；false=重复消息（请跳过）
     */
    public boolean acquire(MqMessage<?> envelope, int maxRetry) {
        AsyncTask existing = findByMessageId(envelope.getMessageId());
        if (existing != null) {
            return acquireExisting(envelope, existing);
        }

        AsyncTask task = new AsyncTask();
        task.setMessageId(envelope.getMessageId());
        task.setBizType(envelope.getBizType());
        task.setBizId(envelope.getBizId());
        task.setUserId(envelope.getUserId());
        task.setTraceId(envelope.getTraceId());
        task.setStatus("RUNNING");
        task.setRetryCount(envelope.getRetryCount() == null ? 0 : envelope.getRetryCount());
        task.setMaxRetry(maxRetry);
        task.setPayload(toJson(envelope.getPayload()));
        task.setStartedAt(LocalDateTime.now());
        try {
            asyncTaskMapper.insert(task);
        } catch (DuplicateKeyException duplicate) {
            AsyncTask concurrent = findByMessageId(envelope.getMessageId());
            if (concurrent != null) {
                return acquireExisting(envelope, concurrent);
            }
            throw duplicate;
        }
        Boolean ok = rememberConsumed(envelope.getMessageId());
        if (!Boolean.TRUE.equals(ok)) {
            log.info("MQ Redis consumed key already exists after durable task insert, continue messageId={}",
                    envelope.getMessageId());
        }
        return true;
    }

    private AsyncTask findByMessageId(String messageId) {
        return asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, messageId)
                        .last("limit 1"));
    }

    private boolean acquireExisting(MqMessage<?> envelope, AsyncTask existing) {
        if ("SUCCESS".equals(existing.getStatus()) || "DEAD".equals(existing.getStatus())) {
            log.info("MQ duplicate terminal task, skip messageId={}, status={}",
                    envelope.getMessageId(), existing.getStatus());
            return false;
        }
        Boolean ok = rememberConsumed(envelope.getMessageId());
        if (!Boolean.TRUE.equals(ok)) {
            log.info("MQ duplicate active task, skip messageId={}, status={}",
                    envelope.getMessageId(), existing.getStatus());
            return false;
        }
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getId, existing.getId())
                        .set(AsyncTask::getStatus, "RUNNING")
                        .set(AsyncTask::getFailureReason, null)
                        .set(AsyncTask::getCompletedAt, null)
                        .set(AsyncTask::getStartedAt, LocalDateTime.now()));
        return true;
    }

    private Boolean rememberConsumed(String messageId) {
        return redisTemplate.opsForValue()
                .setIfAbsent(RedisKeyConstants.mqConsumedKey(messageId), "1", Duration.ofDays(7));
    }

    public void markSuccess(String messageId, Object result) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, messageId)
                        .set(AsyncTask::getStatus, "SUCCESS")
                        .set(AsyncTask::getResult, result == null ? null : toJson(result))
                        .set(AsyncTask::getCompletedAt, LocalDateTime.now()));
    }

    public void markFailed(String messageId, String reason) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, messageId)
                        .set(AsyncTask::getStatus, "FAILED")
                        .set(AsyncTask::getFailureReason, truncate(safeFailureReason(reason), 2000))
                        .set(AsyncTask::getCompletedAt, LocalDateTime.now())
                        .setSql("retry_count = retry_count + 1"));
        // 失败后释放 Redis 幂等键，交给 MQ 或人工补偿流程再次投递。
        redisTemplate.delete(RedisKeyConstants.mqConsumedKey(messageId));
    }

    public void markTerminalFailed(String messageId, String reason) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, messageId)
                        .set(AsyncTask::getStatus, "FAILED")
                        .set(AsyncTask::getFailureReason, truncate(safeFailureReason(reason), 2000))
                        .set(AsyncTask::getCompletedAt, LocalDateTime.now()));
    }

    public void markDead(MqMessage<?> envelope, String reason) {
        // 超过重试阈值后保留死信记录，后台可按 bizType 解析 payload 后人工恢复。
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, envelope.getMessageId())
                        .set(AsyncTask::getStatus, "DEAD"));

        MessageDeadLetter dlq = new MessageDeadLetter();
        dlq.setMessageId(envelope.getMessageId());
        dlq.setBizType(envelope.getBizType());
        dlq.setBizId(envelope.getBizId());
        dlq.setUserId(envelope.getUserId());
        dlq.setTraceId(envelope.getTraceId());
        dlq.setPayload(toJson(envelope.getPayload()));
        dlq.setLastFailureReason(truncate(safeFailureReason(reason), 2000));
        dlq.setTotalRetry(envelope.getRetryCount() == null ? 0 : envelope.getRetryCount());
        dlq.setHandleStatus("UNHANDLED");
        deadLetterMapper.insert(dlq);
    }

    public void prepareManualRetry(Long taskId, String messageId) {
        if (messageId != null && !messageId.isBlank()) {
            redisTemplate.delete(RedisKeyConstants.mqConsumedKey(messageId));
        }
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getId, taskId)
                        .set(AsyncTask::getStatus, "PENDING")
                        .set(AsyncTask::getFailureReason, null)
                        .set(AsyncTask::getResult, null)
                        .set(AsyncTask::getStartedAt, null)
                        .set(AsyncTask::getCompletedAt, null)
                        .set(AsyncTask::getUpdatedAt, LocalDateTime.now()));
    }

    public void markManualRetryDispatchFailed(Long taskId, String reason) {
        asyncTaskMapper.update(null,
                new LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getId, taskId)
                        .set(AsyncTask::getStatus, "FAILED")
                        .set(AsyncTask::getFailureReason, truncate(safeFailureReason(reason), 2000))
                        .set(AsyncTask::getCompletedAt, LocalDateTime.now())
                        .set(AsyncTask::getUpdatedAt, LocalDateTime.now()));
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }

    private String safeFailureReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "Async task failed. Check service logs with traceId.";
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        if (lower.contains("authorization") || lower.contains("bearer") || lower.contains("token")
                || lower.contains("api key") || lower.contains("apikey") || lower.contains("secret")
                || lower.contains("password")) {
            return "Async task failed because an upstream credential or authorization check failed.";
        }
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("connection")
                || lower.contains("connect") || lower.contains("503") || lower.contains("502")
                || lower.contains("load balancer") || lower.contains("feign")) {
            return "Async task failed because an upstream service is temporarily unavailable.";
        }
        if (lower.contains("json") || lower.contains("parse") || lower.contains("deserialize")) {
            return "Async task failed because an upstream response could not be parsed.";
        }
        return "Async task failed. Check service logs with traceId.";
    }
}
