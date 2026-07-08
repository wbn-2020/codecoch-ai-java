package com.codecoachai.question.task;

import com.codecoachai.common.redis.lock.DistributedLockHelper;
import com.codecoachai.question.domain.enums.QuestionReviewStatus;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionReviewRetentionCleanupTask {

    private static final String RETENTION_CLEANUP_LOCK_KEY = "codecoachai:lock:question-review:retention-cleanup";
    private static final String PENDING_STATUS = QuestionReviewStatus.PENDING.name();
    private static final List<String> TERMINAL_STATUSES = List.of(
            QuestionReviewStatus.APPROVED.name(),
            QuestionReviewStatus.REJECTED.name(),
            QuestionReviewStatus.CANCELLED.name()
    );
    private static final String FIND_EXPIRED_REVIEW_IDS = """
            SELECT id
            FROM question_review
            WHERE deleted = 0
              AND updated_at <= ?
              AND review_status IN (?, ?, ?)
              AND raw_ai_result_json IS NOT NULL
              AND raw_ai_result_json <> ''
            ORDER BY updated_at ASC
            LIMIT ?
            """;
    private static final String FIND_STALE_PENDING_REVIEW_IDS = """
            SELECT id
            FROM question_review
            WHERE deleted = 0
              AND created_at <= ?
              AND review_status = ?
              AND raw_ai_result_json IS NOT NULL
              AND raw_ai_result_json <> ''
            ORDER BY created_at ASC
            LIMIT ?
            """;
    private static final String SCRUB_RAW_FIELDS_TEMPLATE = """
            UPDATE question_review
            SET raw_ai_result_json = NULL
            WHERE deleted = 0
              AND id IN (%s)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DistributedLockHelper distributedLockHelper;

    @Value("${codecoachai.question.review.retention-cleanup.enabled:true}")
    private boolean enabled;

    @Value("${codecoachai.question.review.retention-cleanup.retention-days:30}")
    private long retentionDays;

    @Value("${codecoachai.question.review.retention-cleanup.pending-retention-days:7}")
    private long pendingRetentionDays;

    @Value("${codecoachai.question.review.retention-cleanup.scan-limit:100}")
    private int scanLimit;

    @Value("${codecoachai.question.review.retention-cleanup.lock-wait-seconds:0}")
    private long lockWaitSeconds;

    @Value("${codecoachai.question.review.retention-cleanup.lock-lease-seconds:300}")
    private long lockLeaseSeconds;

    @Scheduled(cron = "${codecoachai.question.review.retention-cleanup.cron:0 35 3 * * ?}")
    public void cleanupExpiredRawFields() {
        if (!enabled) {
            return;
        }
        try {
            boolean acquired = distributedLockHelper.tryLockAndRun(
                    RETENTION_CLEANUP_LOCK_KEY,
                    lockWaitSeconds,
                    lockLeaseSeconds,
                    this::cleanupCandidates);
            if (!acquired) {
                log.debug("Question review retention cleanup skipped because another run is active");
            }
        } catch (Exception ex) {
            log.error("Question review retention cleanup failed", ex);
        }
    }

    private void cleanupCandidates() {
        int boundedScanLimit = Math.max(scanLimit, 1);
        scrubTerminalReviewRawFields(boundedScanLimit);
        scrubStalePendingReviewRawFields(boundedScanLimit);
    }

    private void scrubTerminalReviewRawFields(int boundedScanLimit) {
        if (retentionDays <= 0L) {
            log.warn("Skip question_review terminal retention scrub because retentionDays={} is not positive", retentionDays);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(retentionDays, 0L));
        Object[] args = {
                Timestamp.valueOf(cutoff),
                TERMINAL_STATUSES.get(0),
                TERMINAL_STATUSES.get(1),
                TERMINAL_STATUSES.get(2),
                boundedScanLimit
        };
        int[] argTypes = {
                Types.TIMESTAMP,
                Types.VARCHAR,
                Types.VARCHAR,
                Types.VARCHAR,
                Types.INTEGER
        };
        scrubRawFields(FIND_EXPIRED_REVIEW_IDS, args, argTypes, "expired");
    }

    private void scrubStalePendingReviewRawFields(int boundedScanLimit) {
        if (pendingRetentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(pendingRetentionDays);
        Object[] args = {
                Timestamp.valueOf(cutoff),
                PENDING_STATUS,
                boundedScanLimit
        };
        int[] argTypes = {
                Types.TIMESTAMP,
                Types.VARCHAR,
                Types.INTEGER
        };
        scrubRawFields(FIND_STALE_PENDING_REVIEW_IDS, args, argTypes, "stale pending");
    }

    private void scrubRawFields(String querySql, Object[] queryArgs, int[] queryArgTypes, String scopeLabel) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, queryArgs, queryArgTypes);
        List<Long> ids = extractIds(rows);
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String updateSql = SCRUB_RAW_FIELDS_TEMPLATE.formatted(placeholders);
        Object[] updateArgs = ids.toArray();
        int[] updateArgTypes = new int[ids.size()];
        Arrays.fill(updateArgTypes, Types.BIGINT);
        int updated = jdbcTemplate.update(updateSql, updateArgs, updateArgTypes);
        if (updated > 0) {
            log.info("Scrubbed raw AI payloads from {} {} question_review rows", updated, scopeLabel);
        }
    }

    private List<Long> extractIds(List<Map<String, Object>> rows) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            if (id == null) {
                log.warn("Skip question_review retention cleanup row because id is missing: {}", safeRowSummary(row));
                continue;
            }
            ids.add(id);
        }
        return ids;
    }

    private Map<String, Object> safeRowSummary(Map<String, Object> row) {
        if (row == null) {
            return Map.of("fieldCount", 0);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fieldCount", row.size());
        summary.put("fields", row.keySet());
        return summary;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return Long.parseLong(trimmed);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }
}
