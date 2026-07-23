package com.codecoachai.ai.task;

import com.codecoachai.common.redis.lock.DistributedLockHelper;
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
public class AiCallLogRetentionCleanupTask {

    private static final String RETENTION_CLEANUP_LOCK_KEY = "codecoachai:lock:ai-call-log:retention-cleanup";
    private static final String FIND_EXPIRED_LOG_IDS = """
            SELECT id
            FROM ai_call_log
            WHERE deleted = 0
              AND created_at <= ?
              AND (
                  (input_variables_json IS NOT NULL AND input_variables_json <> '')
                  OR (model_params_json IS NOT NULL AND model_params_json <> '')
                  OR (request_prompt IS NOT NULL AND request_prompt <> '')
                  OR (response_content IS NOT NULL AND response_content <> '')
                  OR (request_body IS NOT NULL AND request_body <> '')
                  OR (response_body IS NOT NULL AND response_body <> '')
              )
            ORDER BY created_at ASC
            LIMIT ?
            """;
    private static final String SCRUB_RAW_FIELDS_TEMPLATE = """
            UPDATE ai_call_log
            SET input_variables_json = NULL,
                model_params_json = NULL,
                request_prompt = NULL,
                response_content = NULL,
                request_body = NULL,
                response_body = NULL
            WHERE deleted = 0
              AND id IN (%s)
            """;
    private static final String FIND_HARD_DELETE_LOG_IDS = """
            SELECT id
            FROM ai_call_log
            WHERE deleted = 0
              AND created_at <= ?
              AND (input_variables_json IS NULL OR input_variables_json = '')
              AND (model_params_json IS NULL OR model_params_json = '')
              AND (request_prompt IS NULL OR request_prompt = '')
              AND (response_content IS NULL OR response_content = '')
              AND (request_body IS NULL OR request_body = '')
              AND (response_body IS NULL OR response_body = '')
            ORDER BY created_at ASC
            LIMIT ?
            """;
    private static final String DELETE_LOGS_TEMPLATE = """
            DELETE FROM ai_call_log
            WHERE deleted = 0
              AND id IN (%s)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DistributedLockHelper distributedLockHelper;

    @Value("${codecoachai.ai.call-log.retention-cleanup.enabled:true}")
    private boolean enabled;

    @Value("${codecoachai.ai.call-log.retention-cleanup.retention-days:30}")
    private long retentionDays;

    @Value("${codecoachai.ai.call-log.retention-cleanup.hard-delete-days:180}")
    private long hardDeleteDays;

    @Value("${codecoachai.ai.call-log.retention-cleanup.scan-limit:200}")
    private int scanLimit;

    @Value("${codecoachai.ai.call-log.retention-cleanup.lock-wait-seconds:0}")
    private long lockWaitSeconds;

    @Value("${codecoachai.ai.call-log.retention-cleanup.lock-lease-seconds:300}")
    private long lockLeaseSeconds;

    @Scheduled(cron = "${codecoachai.ai.call-log.retention-cleanup.cron:0 15 3 * * ?}")
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
                log.debug("AI call log retention cleanup skipped because another run is active");
            }
        } catch (Exception ex) {
            log.error("AI call log retention cleanup failed", ex);
        }
    }

    private void cleanupCandidates() {
        LocalDateTime now = LocalDateTime.now();
        scrubExpiredRawFields(now);
        hardDeleteDeeplyExpiredRows(now);
    }

    private void scrubExpiredRawFields(LocalDateTime now) {
        if (retentionDays <= 0L) {
            log.warn("Skip ai_call_log retention scrub because retentionDays={} is not positive", retentionDays);
            return;
        }
        LocalDateTime cutoff = now.minusDays(Math.max(retentionDays, 0L));
        Object[] args = {
                Timestamp.valueOf(cutoff),
                Math.max(scanLimit, 1)
        };
        int[] argTypes = {Types.TIMESTAMP, Types.INTEGER};
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FIND_EXPIRED_LOG_IDS, args, argTypes);
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
            log.info("Scrubbed raw fields from {} expired ai_call_log rows", updated);
        }
    }

    private void hardDeleteDeeplyExpiredRows(LocalDateTime now) {
        if (hardDeleteDays <= 0L) {
            return;
        }
        long effectiveHardDeleteDays = Math.max(hardDeleteDays, Math.max(retentionDays, 0L));
        LocalDateTime cutoff = now.minusDays(effectiveHardDeleteDays);
        Object[] args = {
                Timestamp.valueOf(cutoff),
                Math.max(scanLimit, 1)
        };
        int[] argTypes = {Types.TIMESTAMP, Types.INTEGER};
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FIND_HARD_DELETE_LOG_IDS, args, argTypes);
        List<Long> ids = extractIds(rows);
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String deleteSql = DELETE_LOGS_TEMPLATE.formatted(placeholders);
        Object[] deleteArgs = ids.toArray();
        int[] deleteArgTypes = new int[ids.size()];
        Arrays.fill(deleteArgTypes, Types.BIGINT);
        int deleted = jdbcTemplate.update(deleteSql, deleteArgs, deleteArgTypes);
        if (deleted > 0) {
            log.info("Hard deleted {} expired ai_call_log rows beyond retention window", deleted);
        }
    }

    private List<Long> extractIds(List<Map<String, Object>> rows) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            if (id == null) {
                log.warn("Skip ai_call_log retention cleanup row because id is missing: {}", safeRowSummary(row));
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
