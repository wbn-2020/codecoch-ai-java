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
import java.util.List;
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
        return acquire(envelope, maxRetry, false);
    }

    /**
     * Claims a task that may have been registered by business key before MQ dispatch.
     *
     * <p>When the producer-side registration still has its provisional message id,
     * the first consumer atomically binds the real MQ message id and then claims
     * the existing PENDING row.
     */
    public boolean acquireRegistered(MqMessage<?> envelope, int maxRetry) {
        return acquire(envelope, maxRetry, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public AsyncTask registerPending(String messageId,
                                     String bizType,
                                     String bizId,
                                     Long userId,
                                     String traceId,
                                     Object payload,
                                     int maxRetry) {
        return registerPending(messageId, bizType, bizId, userId, traceId, null, payload, maxRetry);
    }

    @Transactional(rollbackFor = Exception.class)
    public AsyncTask registerPending(String messageId,
                                     String bizType,
                                     String bizId,
                                     Long userId,
                                     String traceId,
                                     String executionId,
                                     Object payload,
                                     int maxRetry) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(bizType)) {
            throw new IllegalArgumentException("Async task messageId and bizType are required");
        }
        AsyncTask existing = findByMessageId(messageId);
        if (existing != null) {
            return attachExecutionId(existing, executionId);
        }
        LocalDateTime now = leaseTimestamp();
        AsyncTask task = new AsyncTask();
        task.setMessageId(messageId);
        task.setBizType(bizType);
        task.setBizId(bizId);
        task.setUserId(userId);
        task.setTraceId(traceId);
        task.setExecutionId(trimToNull(executionId));
        if (StringUtils.hasText(executionId)) {
            task.setAttemptNo(1);
        }
        task.setStatus(STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetry(effectiveMaxRetry(maxRetry));
        task.setPayload(toJson(payload));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            asyncTaskMapper.insert(task);
            return task;
        } catch (DuplicateKeyException duplicate) {
            AsyncTask concurrent = findByMessageId(messageId);
            if (concurrent != null) {
                return attachExecutionId(concurrent, executionId);
            }
            throw duplicate;
        }
    }

    public AsyncTask findLatestForUser(String bizType, String bizId, Long userId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId) || userId == null) {
            return null;
        }
        return asyncTaskMapper.selectOne(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getBizType, bizType)
                .eq(AsyncTask::getBizId, bizId)
                .eq(AsyncTask::getUserId, userId)
                .eq(AsyncTask::getDeleted, 0)
                .orderByDesc(AsyncTask::getCreatedAt)
                .last("limit 1"));
    }

    public List<AsyncTask> findStalePending(String bizType, LocalDateTime before, int limit) {
        if (!StringUtils.hasText(bizType) || before == null || limit <= 0) {
            return List.of();
        }
        return asyncTaskMapper.selectList(new LambdaQueryWrapper<AsyncTask>()
                .eq(AsyncTask::getBizType, bizType)
                .eq(AsyncTask::getStatus, STATUS_PENDING)
                .eq(AsyncTask::getDeleted, 0)
                .le(AsyncTask::getCreatedAt, before)
                .orderByAsc(AsyncTask::getCreatedAt)
                .last("limit " + Math.min(limit, 200)));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean failPendingIfUnclaimed(String messageId, String reason) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        return asyncTaskMapper.completePendingTask(
                messageId,
                STATUS_FAILED,
                truncate(safeFailureReason(reason), 2000),
                null,
                leaseTimestamp()) == 1;
    }

    private boolean acquire(MqMessage<?> envelope, int maxRetry, boolean reuseRegisteredTask) {
        if (envelope == null || !StringUtils.hasText(envelope.getMessageId())) {
            throw new IllegalArgumentException("MQ messageId is required");
        }

        AsyncTask existing = findByMessageId(envelope.getMessageId());
        if (existing != null) {
            return acquireExisting(envelope, existing, effectiveMaxRetry(maxRetry));
        }
        if (reuseRegisteredTask) {
            AsyncTask registered = findRegistration(envelope);
            if (registered != null) {
                if (STATUS_RUNNING.equals(registered.getStatus())) {
                    return acquireExpiredRegisteredRunning(envelope, registered,
                            effectiveMaxRetry(maxRetry));
                }
                if (!STATUS_PENDING.equals(registered.getStatus())) {
                    log.info("MQ registered task already claimed or completed messageId={} bizType={} bizId={} status={}",
                            envelope.getMessageId(), envelope.getBizType(), envelope.getBizId(),
                            registered.getStatus());
                    return false;
                }
                AsyncTask bound = bindRegisteredMessage(envelope, registered, effectiveMaxRetry(maxRetry));
                if (bound == null) {
                    return false;
                }
                return acquireExisting(envelope, bound, effectiveMaxRetry(maxRetry));
            }
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

    private boolean acquireExpiredRegisteredRunning(MqMessage<?> envelope,
                                                    AsyncTask registered,
                                                    int maxRetry) {
        LocalDateTime now = leaseTimestamp();
        if (!isDatabaseLeaseExpired(registered, now)) {
            log.info("MQ registered task has an active database lease messageId={} bizType={} bizId={}",
                    envelope.getMessageId(), envelope.getBizType(), envelope.getBizId());
            return false;
        }
        String leaseToken = nextLeaseToken();
        int updated;
        try {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AsyncTask> claim =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AsyncTask>()
                            .eq(AsyncTask::getId, registered.getId())
                            .eq(AsyncTask::getMessageId, registered.getMessageId())
                            .eq(AsyncTask::getStatus, STATUS_RUNNING)
                            .set(AsyncTask::getMessageId, envelope.getMessageId())
                            .set(AsyncTask::getTraceId, envelope.getTraceId())
                            .set(AsyncTask::getUserId, envelope.getUserId())
                            .set(AsyncTask::getPayload, toJson(envelope.getPayload()))
                            .set(AsyncTask::getLeaseToken, leaseToken)
                            .set(AsyncTask::getStartedAt, now)
                            .set(AsyncTask::getMaxRetry,
                                    Math.max(maxRetry, registered.getMaxRetry() == null
                                            ? 0 : registered.getMaxRetry()))
                            .set(AsyncTask::getUpdatedAt, now);
            if (StringUtils.hasText(registered.getLeaseToken())) {
                claim.eq(AsyncTask::getLeaseToken, registered.getLeaseToken());
            } else {
                claim.isNull(AsyncTask::getLeaseToken);
            }
            LocalDateTime expiresBefore = leaseExpiresBefore(now);
            claim.and(wrapper -> wrapper.isNull(AsyncTask::getStartedAt)
                    .or()
                    .le(AsyncTask::getStartedAt, expiresBefore));
            updated = asyncTaskMapper.update(null, claim);
        } catch (DuplicateKeyException duplicate) {
            AsyncTask concurrent = findByMessageId(envelope.getMessageId());
            if (concurrent != null) {
                return acquireExisting(envelope, concurrent, maxRetry);
            }
            throw duplicate;
        }
        if (updated != 1) {
            log.info("MQ registered RUNNING task takeover lost CAS race messageId={} bizType={} bizId={}",
                    envelope.getMessageId(), envelope.getBizType(), envelope.getBizId());
            return false;
        }
        int retryCount = Math.max(0,
                registered.getRetryCount() == null ? 0 : registered.getRetryCount());
        return activateLease(newLease(envelope, retryCount, maxRetry, leaseToken));
    }

    private AsyncTask findRegistration(MqMessage<?> envelope) {
        if (!StringUtils.hasText(envelope.getBizType()) || !StringUtils.hasText(envelope.getBizId())) {
            return null;
        }
        return asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getBizType, envelope.getBizType())
                        .eq(AsyncTask::getBizId, envelope.getBizId())
                        .orderByDesc(AsyncTask::getCreatedAt)
                        .last("limit 1"));
    }

    private AsyncTask bindRegisteredMessage(MqMessage<?> envelope, AsyncTask registered, int maxRetry) {
        LocalDateTime now = leaseTimestamp();
        int updated;
        try {
            updated = asyncTaskMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AsyncTask>()
                            .eq(AsyncTask::getId, registered.getId())
                            .eq(AsyncTask::getMessageId, registered.getMessageId())
                            .eq(AsyncTask::getStatus, STATUS_PENDING)
                            .isNull(AsyncTask::getLeaseToken)
                            .set(AsyncTask::getMessageId, envelope.getMessageId())
                            .set(AsyncTask::getTraceId, envelope.getTraceId())
                            .set(AsyncTask::getUserId, envelope.getUserId())
                            .set(AsyncTask::getPayload, toJson(envelope.getPayload()))
                            .set(AsyncTask::getMaxRetry,
                                    Math.max(maxRetry, registered.getMaxRetry() == null ? 0 : registered.getMaxRetry()))
                            .set(AsyncTask::getUpdatedAt, now));
        } catch (DuplicateKeyException duplicate) {
            AsyncTask concurrent = findByMessageId(envelope.getMessageId());
            if (concurrent != null) {
                return concurrent;
            }
            throw duplicate;
        }
        if (updated != 1) {
            log.info("MQ registered task binding lost CAS race messageId={} bizType={} bizId={}",
                    envelope.getMessageId(), envelope.getBizType(), envelope.getBizId());
            return null;
        }
        registered.setMessageId(envelope.getMessageId());
        registered.setTraceId(envelope.getTraceId());
        registered.setUserId(envelope.getUserId());
        registered.setPayload(toJson(envelope.getPayload()));
        registered.setMaxRetry(Math.max(maxRetry,
                registered.getMaxRetry() == null ? 0 : registered.getMaxRetry()));
        registered.setUpdatedAt(now);
        return registered;
    }

    private AsyncTask findByMessageId(String messageId) {
        return asyncTaskMapper.selectOne(
                new LambdaQueryWrapper<AsyncTask>()
                        .eq(AsyncTask::getMessageId, messageId)
                        .last("limit 1"));
    }

    private AsyncTask attachExecutionId(AsyncTask task, String executionId) {
        String normalizedExecutionId = trimToNull(executionId);
        if (task == null || normalizedExecutionId == null) {
            return task;
        }
        if (StringUtils.hasText(task.getExecutionId())) {
            if (!normalizedExecutionId.equals(task.getExecutionId())) {
                throw new IllegalStateException("Async task executionId conflicts with existing registration");
            }
            return task;
        }

        LocalDateTime now = leaseTimestamp();
        int updated = asyncTaskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AsyncTask>()
                        .eq(AsyncTask::getId, task.getId())
                        .and(wrapper -> wrapper.isNull(AsyncTask::getExecutionId)
                                .or()
                                .eq(AsyncTask::getExecutionId, ""))
                        .set(AsyncTask::getExecutionId, normalizedExecutionId)
                        .set(AsyncTask::getAttemptNo, 1)
                        .set(AsyncTask::getUpdatedAt, now));
        if (updated == 1) {
            task.setExecutionId(normalizedExecutionId);
            task.setAttemptNo(1);
            task.setUpdatedAt(now);
            return task;
        }

        AsyncTask current = findByMessageId(task.getMessageId());
        if (current != null && normalizedExecutionId.equals(current.getExecutionId())) {
            return current;
        }
        throw new IllegalStateException("Async task executionId could not be attached");
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
            leaseToken = nextLeaseToken();
            if (StringUtils.hasText(existing.getLeaseToken())) {
                updated = asyncTaskMapper.stealRunningTask(
                        existing.getId(),
                        existing.getLeaseToken(),
                        leaseToken,
                        now,
                        leaseExpiresBefore(now),
                        maxRetry,
                        now);
            } else {
                updated = asyncTaskMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AsyncTask>()
                                .eq(AsyncTask::getId, existing.getId())
                                .eq(AsyncTask::getStatus, STATUS_RUNNING)
                                .isNull(AsyncTask::getLeaseToken)
                                .and(wrapper -> wrapper.isNull(AsyncTask::getStartedAt)
                                        .or()
                                        .le(AsyncTask::getStartedAt, leaseExpiresBefore(now)))
                                .set(AsyncTask::getLeaseToken, leaseToken)
                                .set(AsyncTask::getStartedAt, now)
                                .set(AsyncTask::getMaxRetry,
                                        Math.max(maxRetry, existing.getMaxRetry() == null
                                                ? 0 : existing.getMaxRetry()))
                                .set(AsyncTask::getUpdatedAt, now));
            }
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
    public boolean markFailed(String messageId, String reason) {
        LeaseHandle lease = requireCurrentLease(messageId);
        try {
            LocalDateTime now = leaseTimestamp();
            String safeReason = truncate(safeFailureReason(reason), 2000);
            int pending = asyncTaskMapper.markRetryableFailed(
                    messageId, lease.leaseToken, safeReason, now);
            boolean terminal = pending == 0;
            if (pending == 0) {
                int dead = asyncTaskMapper.markRetryExhaustedDead(
                        messageId, lease.leaseToken, safeReason, now);
                requireSingleUpdate(dead, messageId);
                deadLetterMapper.upsert(buildDeadLetter(lease, safeReason));
            }
            finalizeLeaseAfterTransaction(lease);
            return terminal;
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

    /**
     * Starts a manual retry as a child execution. The failed task remains an
     * auditable parent; the new MQ message gets its own async_task row.
     */
    @Transactional(rollbackFor = Exception.class)
    public AsyncTask prepareManualRetry(AsyncTask parentTask, ManualRetryAttempt attempt) {
        if (parentTask == null || parentTask.getId() == null
                || !StringUtils.hasText(parentTask.getMessageId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Parent async task is invalid");
        }
        if (attempt == null
                || !StringUtils.hasText(attempt.messageId())
                || !StringUtils.hasText(attempt.executionId())
                || !StringUtils.hasText(attempt.parentExecutionId())
                || !StringUtils.hasText(attempt.payload())
                || !StringUtils.hasText(attempt.retryPreviewHash())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Manual retry execution identity is invalid");
        }
        LocalDateTime now = leaseTimestamp();
        int parentUpdated = asyncTaskMapper.markManualRetryParentDispatched(
                parentTask.getId(),
                parentTask.getMessageId(),
                attempt.retryPreviewHash(),
                attempt.parentExecutionId(),
                attempt.executionId(),
                attempt.attemptNo(),
                parentTask.getUpdatedAt(),
                now);
        if (parentUpdated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Task governance changed; refresh retry preview before dispatching");
        }

        AsyncTask retryTask = new AsyncTask();
        retryTask.setMessageId(attempt.messageId());
        retryTask.setBizType(parentTask.getBizType());
        retryTask.setBizId(parentTask.getBizId());
        retryTask.setUserId(parentTask.getUserId());
        retryTask.setTraceId(attempt.traceId());
        retryTask.setExecutionId(attempt.executionId());
        retryTask.setParentExecutionId(attempt.parentExecutionId());
        retryTask.setRunId(parentTask.getRunId());
        retryTask.setAttemptNo(attempt.attemptNo());
        retryTask.setIdempotencyKey(attempt.idempotencyKey());
        retryTask.setStatus(STATUS_PENDING);
        retryTask.setRetryCount(0);
        retryTask.setMaxRetry(parentTask.getMaxRetry());
        retryTask.setPayload(attempt.payload());
        retryTask.setGovernanceStatus("RETRYING");
        retryTask.setGovernanceReason("Manual retry dispatch is in progress");
        retryTask.setGovernanceOwner(parentTask.getGovernanceOwner());
        retryTask.setGovernanceUpdatedAt(now);
        retryTask.setRetryPreviewHash(attempt.retryPreviewHash());
        retryTask.setCreatedAt(now);
        retryTask.setUpdatedAt(now);
        if (asyncTaskMapper.insert(retryTask) != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "Manual retry task registration failed");
        }
        if (StringUtils.hasText(parentTask.getMessageId())) {
            runAfterCommit(() -> deleteRedisKey(parentTask.getMessageId()));
        }
        return retryTask;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markManualRetryDispatchFailed(Long parentTaskId,
                                              Long retryTaskId,
                                              String childExecutionId,
                                              String reason) {
        LocalDateTime failedAt = leaseTimestamp();
        int updated = asyncTaskMapper.markManualRetryDispatchFailed(
                retryTaskId,
                null,
                truncate(safeFailureReason(reason), 2000),
                failedAt);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Task status changed while retry dispatch was in progress");
        }
        int parentUpdated = asyncTaskMapper.markManualRetryParentDispatchFailed(
                parentTaskId,
                StringUtils.hasText(childExecutionId) ? childExecutionId : "unknown",
                failedAt);
        if (parentUpdated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Parent task governance changed while retry dispatch was in progress");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void completePending(String messageId, boolean successful, Object result, String reason) {
        if (!StringUtils.hasText(messageId)) {
            throw new IllegalArgumentException("Async task messageId is required");
        }
        String status = successful ? STATUS_SUCCESS : STATUS_FAILED;
        int updated = asyncTaskMapper.completePendingTask(
                messageId,
                status,
                successful ? null : truncate(safeFailureReason(reason), 2000),
                successful && result != null ? toJson(result) : null,
                leaseTimestamp());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Task status changed; refresh before completing it");
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

    public record ManualRetryAttempt(String messageId,
                                     String traceId,
                                     String executionId,
                                     String parentExecutionId,
                                     String idempotencyKey,
                                     int attemptNo,
                                     String payload,
                                     String retryPreviewHash) {
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
