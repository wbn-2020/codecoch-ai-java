package com.codecoachai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.mq.domain.MqMessage;
import com.codecoachai.common.redis.constant.RedisKeyConstants;
import com.codecoachai.task.config.AsyncTaskLeaseProperties;
import com.codecoachai.task.config.AsyncTaskLeaseRuntime;
import com.codecoachai.task.domain.entity.AsyncTask;
import com.codecoachai.task.domain.entity.MessageDeadLetter;
import com.codecoachai.task.mapper.AsyncTaskMapper;
import com.codecoachai.task.mapper.MessageDeadLetterMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Async task state machine.
 *
 * <p>The database row is the source of truth. A RUNNING claim is fenced by its
 * persistent {@code lease_token}; {@code started_at} is only the renewable lease
 * freshness timestamp. Redis mirrors the database token for fast duplicate
 * rejection but never grants ownership by itself.
 */
@Slf4j
@Service
public class AsyncTaskService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEAD = "DEAD";

    private static final DefaultRedisScript<Long> RELEASE_LEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RENEW_LEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> REPLACE_LEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then redis.call('psetex', KEYS[1], ARGV[3], ARGV[2]); return 1 "
                    + "else return 0 end",
            Long.class);

    private final AsyncTaskMapper asyncTaskMapper;
    private final MessageDeadLetterMapper deadLetterMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TaskScheduler leaseScheduler;
    private final AsyncTaskLeaseProperties leaseProperties;
    private final AsyncTaskLeaseRuntime leaseRuntime;
    private final ConcurrentMap<String, LeaseHandle> activeLeases = new ConcurrentHashMap<>();
    private final ThreadLocal<LeaseHandle> currentLease = new ThreadLocal<>();

    public AsyncTaskService(AsyncTaskMapper asyncTaskMapper,
                            MessageDeadLetterMapper deadLetterMapper,
                            StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            @Qualifier("asyncTaskLeaseScheduler") TaskScheduler leaseScheduler,
                            AsyncTaskLeaseProperties leaseProperties,
                            AsyncTaskLeaseRuntime leaseRuntime) {
        this.asyncTaskMapper = asyncTaskMapper;
        this.deadLetterMapper = deadLetterMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.leaseScheduler = leaseScheduler;
        this.leaseProperties = leaseProperties;
        this.leaseRuntime = leaseRuntime;
    }

    /**
     * Claims a message for the current consumer thread.
     *
     * @return {@code true} when this consumer owns the fenced RUNNING lease
     */
    public boolean acquire(MqMessage<?> envelope, int maxRetry) {
        if (envelope == null || !StringUtils.hasText(envelope.getMessageId())) {
            throw new IllegalArgumentException("MQ messageId is required");
        }

        AsyncTask existing = findByMessageId(envelope.getMessageId());
        if (existing != null) {
            return acquireExisting(envelope, existing, effectiveMaxRetry(maxRetry));
        }

        LocalDateTime now = leaseTimestamp();
        String leaseToken = nextLeaseToken();
        int effectiveMaxRetry = effectiveMaxRetry(maxRetry);
        int retryCount = Math.max(0, envelope.getRetryCount() == null ? 0 : envelope.getRetryCount());
        AsyncTask task = new AsyncTask();
        task.setMessageId(envelope.getMessageId());
        task.setBizType(envelope.getBizType());
        task.setBizId(envelope.getBizId());
        task.setUserId(envelope.getUserId());
        task.setTraceId(envelope.getTraceId());
        task.setStatus(STATUS_RUNNING);
        task.setLeaseToken(leaseToken);
        task.setRetryCount(retryCount);
        task.setMaxRetry(effectiveMaxRetry);
        task.setPayload(toJson(envelope.getPayload()));
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        try {
            asyncTaskMapper.insert(task);
        } catch (DuplicateKeyException duplicate) {
            AsyncTask concurrent = findByMessageId(envelope.getMessageId());
            if (concurrent != null) {
                return acquireExisting(envelope, concurrent, effectiveMaxRetry);
            }
            throw duplicate;
        }

        return activateLease(newLease(envelope, retryCount, effectiveMaxRetry, leaseToken));
    }

    private AsyncTask findByMessageId(String messageId) {
        return asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, messageId)
                        .last("limit 1"));
    }

    private boolean acquireExisting(MqMessage<?> envelope, AsyncTask existing, int maxRetry) {
        String status = existing.getStatus();
        if (STATUS_SUCCESS.equals(status) || STATUS_DEAD.equals(status) || STATUS_FAILED.equals(status)) {
            log.info("MQ duplicate terminal task skipped messageId={} status={}",
                    envelope.getMessageId(), status);
            return false;
        }

        LocalDateTime now = leaseTimestamp();
        String leaseToken;
        int updated;
        if (STATUS_PENDING.equals(status)) {
            if (StringUtils.hasText(existing.getLeaseToken())) {
                log.warn("MQ PENDING task retains a lease token; claim rejected messageId={}",
                        envelope.getMessageId());
                return false;
            }
            leaseToken = nextLeaseToken();
            updated = asyncTaskMapper.claimReadyTask(
                    existing.getId(),
                    STATUS_PENDING,
                    existing.getLeaseToken(),
                    leaseToken,
                    now,
                    maxRetry,
                    now);
        } else if (STATUS_RUNNING.equals(status)) {
            if (!isDatabaseLeaseExpired(existing, now)) {
                log.info("MQ duplicate active task skipped by database lease messageId={}",
                        envelope.getMessageId());
                return false;
            }
            if (!StringUtils.hasText(existing.getLeaseToken())) {
                log.error("Expired RUNNING task has no persistent lease token; claim rejected messageId={}",
                        envelope.getMessageId());
                return false;
            }
            leaseToken = nextLeaseToken();
            updated = asyncTaskMapper.stealRunningTask(
                    existing.getId(),
                    existing.getLeaseToken(),
                    leaseToken,
                    now,
                    leaseExpiresBefore(now),
                    maxRetry,
                    now);
        } else {
            log.warn("MQ task has unsupported state, skip messageId={} status={}",
                    envelope.getMessageId(), status);
            return false;
        }

        if (updated != 1) {
            log.info("MQ task claim lost CAS race messageId={} expectedStatus={}",
                    envelope.getMessageId(), status);
            return false;
        }
        int retryCount = Math.max(0, existing.getRetryCount() == null ? 0 : existing.getRetryCount());
        return activateLease(newLease(envelope, retryCount, maxRetry, leaseToken));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(String messageId, Object result) {
        LeaseHandle lease = requireCurrentLease(messageId);
        try {
            LocalDateTime now = leaseTimestamp();
            requireSingleUpdate(asyncTaskMapper.markSuccess(
                    messageId, lease.leaseToken, result == null ? null : toJson(result), now), messageId);
            finalizeLeaseAfterTransaction(lease);
        } catch (RuntimeException ex) {
            abandonLease(lease);
            throw ex;
        }
    }

    /**
     * Records a retryable failure. Before the retry budget is exhausted the task
     * returns to PENDING; the final failed attempt atomically transitions to DEAD
     * and creates or refreshes the application dead-letter record.
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String messageId, String reason) {
        LeaseHandle lease = requireCurrentLease(messageId);
        try {
            LocalDateTime now = leaseTimestamp();
            String safeReason = truncate(safeFailureReason(reason), 2000);
            int pending = asyncTaskMapper.markRetryableFailed(
                    messageId, lease.leaseToken, safeReason, now);
            if (pending == 0) {
                int dead = asyncTaskMapper.markRetryExhaustedDead(
                        messageId, lease.leaseToken, safeReason, now);
                requireSingleUpdate(dead, messageId);
                deadLetterMapper.upsert(buildDeadLetter(lease, safeReason));
            }
            finalizeLeaseAfterTransaction(lease);
        } catch (RuntimeException ex) {
            abandonLease(lease);
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markTerminalFailed(String messageId, String reason) {
        LeaseHandle lease = requireCurrentLease(messageId);
        try {
            LocalDateTime now = leaseTimestamp();
            requireSingleUpdate(asyncTaskMapper.markTerminalFailed(
                    messageId,
                    lease.leaseToken,
                    truncate(safeFailureReason(reason), 2000),
                    now), messageId);
            finalizeLeaseAfterTransaction(lease);
        } catch (RuntimeException ex) {
            abandonLease(lease);
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markDead(MqMessage<?> envelope, String reason) {
        if (envelope == null) {
            throw new IllegalArgumentException("MQ envelope is required");
        }
        LeaseHandle lease = requireCurrentLease(envelope.getMessageId());
        try {
            LocalDateTime now = leaseTimestamp();
            String safeReason = truncate(safeFailureReason(reason), 2000);
            requireSingleUpdate(asyncTaskMapper.markDead(
                    envelope.getMessageId(), lease.leaseToken, safeReason, now), envelope.getMessageId());
            deadLetterMapper.upsert(buildDeadLetter(lease, safeReason));
            finalizeLeaseAfterTransaction(lease);
        } catch (RuntimeException ex) {
            abandonLease(lease);
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void prepareManualRetry(Long taskId, String messageId) {
        int updated = asyncTaskMapper.prepareManualRetry(taskId, null, leaseTimestamp());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Task status changed; refresh before retrying");
        }
        if (StringUtils.hasText(messageId)) {
            runAfterCommit(() -> deleteRedisKey(messageId));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markManualRetryDispatchFailed(Long taskId, String reason) {
        int updated = asyncTaskMapper.markManualRetryDispatchFailed(
                taskId,
                null,
                truncate(safeFailureReason(reason), 2000),
                leaseTimestamp());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Task status changed while retry dispatch was in progress");
        }
    }

    private LeaseHandle newLease(MqMessage<?> envelope,
                                 int retryCount,
                                 int maxRetry,
                                 String leaseToken) {
        return new LeaseHandle(
                envelope.getMessageId(),
                envelope.getBizType(),
                envelope.getBizId(),
                envelope.getUserId(),
                envelope.getTraceId(),
                toJson(envelope.getPayload()),
                retryCount,
                maxRetry,
                leaseToken);
    }

    private boolean activateLease(LeaseHandle lease) {
        boolean redisClaimed = claimRedisLease(lease);
        try {
            if (!databaseLeaseOwned(lease)) {
                releaseRedisLease(lease);
                log.info("MQ lease ownership changed before activation messageId={}", lease.messageId);
                return false;
            }
        } catch (RuntimeException ex) {
            if (redisClaimed) {
                releaseRedisLease(lease);
            }
            throw ex;
        }
        if (!redisClaimed) {
            log.warn("Redis lease was not acquired; database lease remains authoritative messageId={}",
                    lease.messageId);
        }

        LeaseHandle previous = activeLeases.put(lease.messageId, lease);
        if (previous != null && previous != lease) {
            previous.cancelHeartbeat();
        }
        currentLease.set(lease);
        try {
            lease.heartbeat = leaseScheduler.scheduleAtFixedRate(
                    () -> renewLease(lease),
                    leaseProperties.effectiveHeartbeatInterval());
            return true;
        } catch (RuntimeException ex) {
            abandonLease(lease);
            throw ex;
        }
    }

    private boolean claimRedisLease(LeaseHandle lease) {
        String key = RedisKeyConstants.mqConsumedKey(lease.messageId);
        Duration duration = leaseProperties.effectiveDuration();
        String currentOwner;
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, lease.leaseToken, duration);
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            currentOwner = redisTemplate.opsForValue().get(key);
            if (lease.leaseToken.equals(currentOwner)) {
                return true;
            }
            if (!StringUtils.hasText(currentOwner)) {
                return Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(key, lease.leaseToken, duration));
            }
            Long replaced = redisTemplate.execute(
                    REPLACE_LEASE_SCRIPT,
                    Collections.singletonList(key),
                    currentOwner,
                    lease.leaseToken,
                    String.valueOf(duration.toMillis()));
            return replaced != null && replaced == 1L;
        } catch (RuntimeException ex) {
            log.warn("Redis lease claim unavailable; database fencing remains authoritative messageId={}",
                    lease.messageId, ex);
            return false;
        }
    }

    private boolean databaseLeaseOwned(LeaseHandle lease) {
        return asyncTaskMapper.verifyLeaseOwner(lease.messageId, lease.leaseToken) == 1;
    }

    private void renewLease(LeaseHandle lease) {
        if (activeLeases.get(lease.messageId) != lease) {
            lease.cancelHeartbeat();
            return;
        }
        try {
            int updated = asyncTaskMapper.renewLease(
                    lease.messageId, lease.leaseToken, leaseTimestamp());
            if (updated != 1) {
                log.warn("MQ lease heartbeat lost database fence messageId={}", lease.messageId);
                abandonLease(lease);
                return;
            }
            if (!renewRedisLease(lease)) {
                log.warn("MQ lease heartbeat lost database ownership after Redis recovery messageId={}",
                        lease.messageId);
                abandonLease(lease);
            }
        } catch (RuntimeException ex) {
            log.warn("MQ lease heartbeat failed and will retry messageId={}", lease.messageId, ex);
        }
    }

    private boolean renewRedisLease(LeaseHandle lease) {
        String key = RedisKeyConstants.mqConsumedKey(lease.messageId);
        Duration duration = leaseProperties.effectiveDuration();
        Long renewed;
        try {
            renewed = redisTemplate.execute(
                    RENEW_LEASE_SCRIPT,
                    Collections.singletonList(key),
                    lease.leaseToken,
                    String.valueOf(duration.toMillis()));
        } catch (RuntimeException ex) {
            log.warn("Redis lease heartbeat unavailable messageId={}", lease.messageId, ex);
            return true;
        }
        if (renewed != null && renewed == 1L) {
            return true;
        }

        boolean claimed = claimRedisLease(lease);
        if (!claimed) {
            log.warn("MQ Redis lease owner differs from database lease messageId={}", lease.messageId);
            return true;
        }
        if (!databaseLeaseOwned(lease)) {
            releaseRedisLease(lease);
            return false;
        }
        return true;
    }

    private boolean isDatabaseLeaseExpired(AsyncTask task, LocalDateTime now) {
        if (task.getStartedAt() == null) {
            return true;
        }
        return !task.getStartedAt().isAfter(leaseExpiresBefore(now));
    }

    private LocalDateTime leaseExpiresBefore(LocalDateTime now) {
        return now.minus(leaseProperties.effectiveDuration());
    }

    private LeaseHandle requireCurrentLease(String messageId) {
        LeaseHandle lease = currentLease.get();
        if (lease == null
                || !lease.messageId.equals(messageId)
                || activeLeases.get(messageId) != lease) {
            throw new TaskLeaseLostException(messageId);
        }
        return lease;
    }

    private void requireSingleUpdate(int updated, String messageId) {
        if (updated != 1) {
            throw new TaskLeaseLostException(messageId);
        }
    }

    private void finalizeLeaseAfterTransaction(LeaseHandle lease) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    finishLease(lease);
                }
            });
            return;
        }
        finishLease(lease);
    }

    private void abandonLease(LeaseHandle lease) {
        finishLease(lease);
    }

    private void finishLease(LeaseHandle lease) {
        if (lease == null) {
            return;
        }
        lease.cancelHeartbeat();
        activeLeases.remove(lease.messageId, lease);
        if (currentLease.get() == lease) {
            currentLease.remove();
        }
        releaseRedisLease(lease);
    }

    private void releaseRedisLease(LeaseHandle lease) {
        try {
            redisTemplate.execute(
                    RELEASE_LEASE_SCRIPT,
                    Collections.singletonList(RedisKeyConstants.mqConsumedKey(lease.messageId)),
                    lease.leaseToken);
        } catch (RuntimeException ex) {
            log.warn("Redis lease release failed; TTL will expire messageId={}", lease.messageId, ex);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void deleteRedisKey(String messageId) {
        try {
            redisTemplate.delete(RedisKeyConstants.mqConsumedKey(messageId));
        } catch (RuntimeException ex) {
            log.warn("Manual retry Redis cleanup failed; TTL or database CAS will recover messageId={}",
                    messageId, ex);
        }
    }

    private MessageDeadLetter buildDeadLetter(LeaseHandle lease, String reason) {
        MessageDeadLetter deadLetter = new MessageDeadLetter();
        deadLetter.setMessageId(lease.messageId);
        deadLetter.setBizType(lease.bizType);
        deadLetter.setBizId(lease.bizId);
        deadLetter.setUserId(lease.userId);
        deadLetter.setTraceId(lease.traceId);
        deadLetter.setPayload(lease.payload);
        deadLetter.setLastFailureReason(reason);
        deadLetter.setTotalRetry(Math.max(0, lease.retryCount));
        deadLetter.setHandleStatus("UNHANDLED");
        return deadLetter;
    }

    private int effectiveMaxRetry(int maxRetry) {
        return Math.max(0, maxRetry);
    }

    private LocalDateTime leaseTimestamp() {
        LocalDateTime now = leaseRuntime.now();
        if (now == null) {
            throw new IllegalStateException("Async task lease runtime returned no timestamp");
        }
        return now;
    }

    private String nextLeaseToken() {
        String token = leaseRuntime.newLeaseToken();
        if (!StringUtils.hasText(token) || token.length() > 64) {
            throw new IllegalStateException("Async task lease runtime returned an invalid token");
        }
        return token;
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
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

    private static final class LeaseHandle {

        private final String messageId;
        private final String bizType;
        private final String bizId;
        private final Long userId;
        private final String traceId;
        private final String payload;
        private final int retryCount;
        private final int maxRetry;
        private final String leaseToken;
        private volatile ScheduledFuture<?> heartbeat;

        private LeaseHandle(String messageId,
                            String bizType,
                            String bizId,
                            Long userId,
                            String traceId,
                            String payload,
                            int retryCount,
                            int maxRetry,
                            String leaseToken) {
            this.messageId = messageId;
            this.bizType = bizType;
            this.bizId = bizId;
            this.userId = userId;
            this.traceId = traceId;
            this.payload = payload;
            this.retryCount = retryCount;
            this.maxRetry = maxRetry;
            this.leaseToken = leaseToken;
        }

        private void cancelHeartbeat() {
            ScheduledFuture<?> scheduled = heartbeat;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }
    }

    public static final class TaskLeaseLostException extends IllegalStateException {

        private TaskLeaseLostException(String messageId) {
            super("Async task lease is no longer owned for messageId=" + messageId);
        }
    }
}
