package com.codecoachai.resume.service.impl;

import com.codecoachai.resume.service.EvidenceProfileFeedbackOutboxService;
import com.codecoachai.resume.service.support.EvidenceProfileFeedbackService;
import com.codecoachai.resume.service.support.EvidenceProfileFeedbackService.ProjectionDisposition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceProfileFeedbackOutboxServiceImpl
        implements EvidenceProfileFeedbackOutboxService {

    private static final int MAX_BATCH_SIZE = 200;
    private static final int STALE_PROCESSING_MINUTES = 5;
    private static final int DEFERRED_RETRY_SECONDS = 300;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final EvidenceProfileFeedbackService feedbackService;

    @Override
    public Long enqueue(Long resultId, Long userId, Integer snapshotVersion) {
        if (resultId == null || userId == null || snapshotVersion == null) {
            return null;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO evidence_profile_feedback_outbox (
                        result_id, user_id, snapshot_version, status, retry_count,
                        next_retry_at, created_at, updated_at, deleted
                    ) VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?, 0)
                    ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, resultId);
            statement.setObject(2, userId);
            statement.setObject(3, snapshotVersion);
            statement.setTimestamp(4, Timestamp.valueOf(now));
            statement.setTimestamp(5, Timestamp.valueOf(now));
            statement.setTimestamp(6, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long outboxId = key == null ? findId(resultId, userId, snapshotVersion) : key.longValue();
        dispatchAfterCommit(outboxId);
        return outboxId;
    }

    @Override
    public int requeueAbilityProjectionForProject(Long userId, Long projectEvidenceId) {
        if (userId == null || projectEvidenceId == null) {
            return 0;
        }
        return jdbcTemplate.update("""
                INSERT INTO evidence_profile_feedback_outbox (
                    result_id, user_id, snapshot_version, status, retry_count,
                    next_retry_at, created_at, updated_at, deleted
                )
                SELECT DISTINCT r.id,
                       r.user_id,
                       r.snapshot_version,
                       'PENDING',
                       0,
                       NOW(),
                       NOW(),
                       NOW(),
                       0
                  FROM career_evidence_usage_result r
                  JOIN career_evidence_usage u
                    ON u.id = r.usage_id
                   AND u.user_id = r.user_id
                   AND u.deleted = 0
                 WHERE r.user_id = ?
                   AND r.deleted = 0
                   AND r.current_snapshot_id IS NOT NULL
                   AND (
                        (u.asset_type = 'PROJECT_EVIDENCE'
                            AND u.asset_id = ?)
                        OR
                        (u.asset_type = 'PROJECT_SKILL_EVIDENCE'
                            AND EXISTS (
                                SELECT 1
                                  FROM project_skill_evidence pse
                                 WHERE pse.id = u.asset_id
                                   AND pse.user_id = ?
                                   AND pse.project_evidence_id = ?
                            ))
                   )
                ON DUPLICATE KEY UPDATE
                    ability_projection_done = 0,
                    status = 'PENDING',
                    retry_count = 0,
                    next_retry_at = NOW(),
                    locked_at = NULL,
                    locked_by = NULL,
                    last_error = NULL,
                    delivered_at = NULL,
                    updated_at = NOW(),
                    deleted = 0
                """, userId, projectEvidenceId, userId, projectEvidenceId);
    }

    @Override
    public boolean dispatch(Long outboxId) {
        if (outboxId == null) {
            return false;
        }
        OutboxRow row;
        try {
            row = requiresNew(() -> claimAndLoad(outboxId));
        } catch (RuntimeException ex) {
            log.error("Evidence feedback outbox claim failed outboxId={}", outboxId, ex);
            return false;
        }
        if (row == null) {
            return false;
        }
        try {
            ProjectionDisposition disposition = requiresNew(() -> {
                acquireUserLock(row.userId());
                return feedbackService.recomputeResult(
                        row.resultId(),
                        row.userId(),
                        row.evidenceProjectionDone(),
                        row.abilityProjectionDone());
            });
            if (ProjectionDisposition.COMPLETED != disposition) {
                requiresNew(() -> {
                    if (!markDeferred(row, disposition)) {
                        throw new IllegalStateException(
                                "Evidence feedback outbox defer fencing failed");
                    }
                    return null;
                });
                return false;
            }
            return Boolean.TRUE.equals(requiresNew(() -> markDone(row)));
        } catch (RuntimeException ex) {
            try {
                requiresNew(() -> {
                    markFailed(row, safeError(ex));
                    return null;
                });
            } catch (RuntimeException markEx) {
                ex.addSuppressed(markEx);
            }
            log.error("Evidence feedback outbox projection failed outboxId={} resultId={} userId={}",
                    row.id(), row.resultId(), row.userId(), ex);
            return false;
        }
    }

    @Override
    public int retryPending(int batchSize) {
        int limit = batchSize <= 0 ? 50 : Math.min(batchSize, MAX_BATCH_SIZE);
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id
                  FROM evidence_profile_feedback_outbox
                 WHERE deleted = 0
                   AND (
                        (status IN ('PENDING', 'FAILED', 'DEFERRED')
                            AND next_retry_at <= NOW())
                        OR (status = 'PROCESSING'
                            AND locked_at < DATE_SUB(NOW(), INTERVAL ? MINUTE))
                   )
                 ORDER BY id
                 LIMIT ?
                """, Long.class, STALE_PROCESSING_MINUTES, limit);
        int processed = 0;
        for (Long id : ids) {
            if (dispatch(id)) {
                processed++;
            }
        }
        return processed;
    }

    private Long findId(Long resultId, Long userId, Integer snapshotVersion) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id
                  FROM evidence_profile_feedback_outbox
                 WHERE result_id = ?
                   AND user_id = ?
                   AND snapshot_version = ?
                   AND deleted = 0
                 LIMIT 1
                """, Long.class, resultId, userId, snapshotVersion);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void dispatchAfterCommit(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        Runnable action = () -> {
            try {
                dispatch(outboxId);
            } catch (RuntimeException ex) {
                log.error("Evidence feedback outbox immediate dispatch failed outboxId={}",
                        outboxId, ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private OutboxRow claimAndLoad(Long outboxId) {
        String workerId = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
                UPDATE evidence_profile_feedback_outbox
                   SET status = 'PROCESSING',
                       locked_at = NOW(),
                       locked_by = ?,
                       updated_at = NOW()
                 WHERE id = ?
                   AND deleted = 0
                   AND (
                        (status IN ('PENDING', 'FAILED', 'DEFERRED')
                            AND next_retry_at <= NOW())
                        OR (status = 'PROCESSING'
                            AND locked_at < DATE_SUB(NOW(), INTERVAL ? MINUTE))
                   )
                """, workerId, outboxId, STALE_PROCESSING_MINUTES);
        if (updated != 1) {
            return null;
        }
        List<OutboxRow> rows = jdbcTemplate.query("""
                SELECT id, result_id, user_id, snapshot_version,
                       evidence_projection_done, ability_projection_done,
                       retry_count, locked_by
                  FROM evidence_profile_feedback_outbox
                 WHERE id = ?
                   AND status = 'PROCESSING'
                   AND locked_by = ?
                   AND deleted = 0
                 LIMIT 1
                """, this::toRow, outboxId, workerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void acquireUserLock(Long userId) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO evidence_profile_feedback_lock (user_id, created_at, updated_at)
                VALUES (?, NOW(), NOW())
                """, userId);
        Long lockedUserId = jdbcTemplate.queryForObject("""
                SELECT user_id
                  FROM evidence_profile_feedback_lock
                 WHERE user_id = ?
                 FOR UPDATE
                """, Long.class, userId);
        if (!userId.equals(lockedUserId)) {
            throw new IllegalStateException("Failed to acquire evidence feedback user lock");
        }
    }

    private boolean markDone(OutboxRow row) {
        return jdbcTemplate.update("""
                UPDATE evidence_profile_feedback_outbox
                   SET status = 'DONE',
                       evidence_projection_done = 1,
                       ability_projection_done = 1,
                       delivered_at = NOW(),
                       last_error = NULL,
                       locked_at = NULL,
                       locked_by = NULL,
                       updated_at = NOW()
                 WHERE id = ?
                   AND status = 'PROCESSING'
                   AND locked_by = ?
                """, row.id(), row.workerId()) == 1;
    }

    private boolean markDeferred(
            OutboxRow row, ProjectionDisposition disposition) {
        return jdbcTemplate.update("""
                UPDATE evidence_profile_feedback_outbox
                   SET status = 'DEFERRED',
                       evidence_projection_done = ?,
                       ability_projection_done = ?,
                       last_error = NULL,
                       next_retry_at = DATE_ADD(NOW(), INTERVAL ? SECOND),
                       locked_at = NULL,
                       locked_by = NULL,
                       updated_at = NOW()
                 WHERE id = ?
                   AND status = 'PROCESSING'
                   AND locked_by = ?
                """,
                disposition.evidenceCompleted() ? 1 : 0,
                disposition.abilityCompleted() ? 1 : 0,
                DEFERRED_RETRY_SECONDS,
                row.id(),
                row.workerId()) == 1;
    }

    private void markFailed(OutboxRow row, String error) {
        int nextRetryCount = row.retryCount() + 1;
        long delaySeconds = Math.min(3600L, 5L * (1L << Math.min(nextRetryCount, 9)));
        jdbcTemplate.update("""
                UPDATE evidence_profile_feedback_outbox
                   SET status = 'FAILED',
                       retry_count = ?,
                       last_error = ?,
                       next_retry_at = DATE_ADD(NOW(), INTERVAL ? SECOND),
                       locked_at = NULL,
                       locked_by = NULL,
                       updated_at = NOW()
                 WHERE id = ?
                   AND status = 'PROCESSING'
                   AND locked_by = ?
                """, nextRetryCount, error, delaySeconds, row.id(), row.workerId());
    }

    private OutboxRow toRow(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRow(
                rs.getLong("id"),
                rs.getLong("result_id"),
                rs.getLong("user_id"),
                rs.getInt("snapshot_version"),
                rs.getBoolean("evidence_projection_done"),
                rs.getBoolean("ability_projection_done"),
                rs.getInt("retry_count"),
                rs.getString("locked_by"));
    }

    private <T> T requiresNew(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private String safeError(Throwable ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex == null ? "unknown projection failure" : ex.getClass().getSimpleName();
        }
        String safe = message.replaceAll("\\s+", " ").trim();
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }

    private record OutboxRow(
            Long id,
            Long resultId,
            Long userId,
            int snapshotVersion,
            boolean evidenceProjectionDone,
            boolean abilityProjectionDone,
            int retryCount,
            String workerId) {
    }
}
