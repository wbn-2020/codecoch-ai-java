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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        String mqKey = RedisKeyConstants.mqConsumedKey(envelope.getMessageId());
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(mqKey, "1", Duration.ofDays(7));
        if (!Boolean.TRUE.equals(ok)) {
            log.info("MQ 重复消费，跳过 messageId={}", envelope.getMessageId());
            return false;
        }

        // 落库 PENDING
        AsyncTask existing = asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, envelope.getMessageId())
                        .last("limit 1"));
        if (existing != null) {
            asyncTaskMapper.update(null,
                    new LambdaUpdateWrapper<AsyncTask>()
                            .eq(AsyncTask::getId, existing.getId())
                            .set(AsyncTask::getStatus, "RUNNING")
                            .set(AsyncTask::getFailureReason, null)
                            .set(AsyncTask::getCompletedAt, null)
                            .set(AsyncTask::getStartedAt, LocalDateTime.now()));
            return true;
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
        asyncTaskMapper.insert(task);
        return true;
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
                        .set(AsyncTask::getFailureReason, truncate(reason, 2000))
                        .set(AsyncTask::getCompletedAt, LocalDateTime.now())
                        .setSql("retry_count = retry_count + 1"));
        redisTemplate.delete(RedisKeyConstants.mqConsumedKey(messageId));
    }

    public void markDead(MqMessage<?> envelope, String reason) {
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
        dlq.setLastFailureReason(truncate(reason, 2000));
        dlq.setTotalRetry(envelope.getRetryCount() == null ? 0 : envelope.getRetryCount());
        dlq.setHandleStatus("UNHANDLED");
        deadLetterMapper.insert(dlq);
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
}
