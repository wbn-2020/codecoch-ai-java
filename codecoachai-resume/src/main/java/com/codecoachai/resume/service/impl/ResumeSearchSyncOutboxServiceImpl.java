package com.codecoachai.resume.service.impl;

import com.codecoachai.resume.mq.ResumeMqDispatcher;
import com.codecoachai.resume.service.ResumeSearchSyncOutboxService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeSearchSyncOutboxServiceImpl implements ResumeSearchSyncOutboxService {

    private static final int MAX_BATCH_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;
    private final Optional<ResumeMqDispatcher> resumeMqDispatcher;

    @Override
    public Long enqueue(Long resumeId, Long userId, String operation) {
        if (resumeId == null || userId == null) {
            return null;
        }
        String normalizedOperation = normalizeOperation(operation);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO resume_search_sync_outbox (
                        resume_id, user_id, operation, status, retry_count, next_retry_at,
                        created_at, updated_at, deleted
                    ) VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, resumeId);
            statement.setObject(2, userId);
            statement.setString(3, normalizedOperation);
            statement.setTimestamp(4, Timestamp.valueOf(now));
            statement.setTimestamp(5, Timestamp.valueOf(now));
            statement.setTimestamp(6, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        Long outboxId = key == null ? null : key.longValue();
        dispatchAfterCommit(outboxId);
        return outboxId;
    }

    @Override
    public boolean dispatch(Long outboxId) {
        if (outboxId == null || !claim(outboxId)) {
            return false;
        }
        OutboxRow row = findById(outboxId);
        if (row == null) {
            return false;
        }
        if (hasNewerEvent(row)) {
            markSuperseded(row.id());
            return true;
        }
        try {
            boolean sent = resumeMqDispatcher
                    .map(dispatcher -> OP_DELETE.equals(row.operation())
                            ? dispatcher.dispatchResumeSearchDelete(row.resumeId(), row.userId())
                            : dispatcher.dispatchResumeSearchUpsert(row.resumeId(), row.userId()))
                    .orElse(false);
            if (!sent) {
                markFailed(row, "search dispatcher unavailable or send returned false");
                return false;
            }
            markDone(row.id());
            return true;
        } catch (RuntimeException ex) {
            markFailed(row, safeError(ex));
            log.error("Resume search outbox dispatch failed outboxId={} resumeId={} op={}",
                    row.id(), row.resumeId(), row.operation(), ex);
            return false;
        }
    }

    @Override
    public int retryPending(int batchSize) {
        int limit = batchSize <= 0 ? 50 : Math.min(batchSize, MAX_BATCH_SIZE);
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id
                  FROM resume_search_sync_outbox
                 WHERE deleted = 0
                   AND (
                        (status IN ('PENDING', 'FAILED') AND next_retry_at <= NOW())
                        OR (status = 'PROCESSING' AND locked_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
                   )
                 ORDER BY id
                 LIMIT ?
                """, Long.class, limit);
        int processed = 0;
        for (Long id : ids) {
            if (dispatch(id)) {
                processed++;
            }
        }
        return processed;
    }

    private void dispatchAfterCommit(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        Runnable action = () -> dispatch(outboxId);
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

    private boolean claim(Long outboxId) {
        String workerId = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
                UPDATE resume_search_sync_outbox
                   SET status = 'PROCESSING', locked_at = NOW(), locked_by = ?, updated_at = NOW()
                 WHERE id = ?
                   AND deleted = 0
                   AND (
                        (status IN ('PENDING', 'FAILED') AND next_retry_at <= NOW())
                        OR (status = 'PROCESSING' AND locked_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
                   )
                """, workerId, outboxId);
        return updated == 1;
    }

    private OutboxRow findById(Long outboxId) {
        List<OutboxRow> rows = jdbcTemplate.query("""
                SELECT id, resume_id, user_id, operation, retry_count
                  FROM resume_search_sync_outbox
                 WHERE id = ? AND deleted = 0
                 LIMIT 1
                """, this::toRow, outboxId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean hasNewerEvent(OutboxRow row) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM resume_search_sync_outbox
                 WHERE resume_id = ? AND id > ? AND deleted = 0
                """, Long.class, row.resumeId(), row.id());
        return count != null && count > 0;
    }

    private void markDone(Long outboxId) {
        jdbcTemplate.update("""
                UPDATE resume_search_sync_outbox
                   SET status = 'DONE', delivered_at = NOW(), last_error = NULL,
                       locked_at = NULL, locked_by = NULL, updated_at = NOW()
                 WHERE id = ?
                """, outboxId);
    }

    private void markSuperseded(Long outboxId) {
        jdbcTemplate.update("""
                UPDATE resume_search_sync_outbox
                   SET status = 'SUPERSEDED', delivered_at = NOW(), last_error = NULL,
                       locked_at = NULL, locked_by = NULL, updated_at = NOW()
                 WHERE id = ?
                """, outboxId);
    }

    private void markFailed(OutboxRow row, String error) {
        int nextRetryCount = row.retryCount() + 1;
        long delaySeconds = Math.min(3600L, 5L * (1L << Math.min(nextRetryCount, 9)));
        jdbcTemplate.update("""
                UPDATE resume_search_sync_outbox
                   SET status = 'FAILED', retry_count = ?, last_error = ?,
                       next_retry_at = DATE_ADD(NOW(), INTERVAL ? SECOND),
                       locked_at = NULL, locked_by = NULL, updated_at = NOW()
                 WHERE id = ?
                """, nextRetryCount, error, delaySeconds, row.id());
    }

    private OutboxRow toRow(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxRow(
                rs.getLong("id"),
                rs.getLong("resume_id"),
                rs.getLong("user_id"),
                rs.getString("operation"),
                rs.getInt("retry_count"));
    }

    private String normalizeOperation(String operation) {
        String normalized = StringUtils.hasText(operation)
                ? operation.trim().toUpperCase(Locale.ROOT)
                : OP_UPSERT;
        return OP_DELETE.equals(normalized) ? OP_DELETE : OP_UPSERT;
    }

    private String safeError(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex == null ? "unknown dispatch failure" : ex.getClass().getSimpleName();
        }
        String safe = message.replaceAll("\\s+", " ").trim();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private record OutboxRow(Long id, Long resumeId, Long userId, String operation, int retryCount) {
    }
}
