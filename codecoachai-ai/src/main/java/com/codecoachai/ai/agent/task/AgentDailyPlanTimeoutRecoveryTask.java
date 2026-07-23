package com.codecoachai.ai.agent.task;

import com.codecoachai.ai.agent.domain.enums.AgentErrorCode;
import com.codecoachai.ai.agent.mq.AgentMqDispatcher;
import com.codecoachai.common.core.util.TextFingerprintUtils;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDailyPlanTimeoutRecoveryTask {

    private static final String TIMEOUT_RECOVERY_LOCK_KEY = "codecoachai:lock:agent:daily-plan:timeout-recovery";
    private static final String AGENT_TYPE = "JOB_COACH";
    private static final String RUNNING_STATUS = "RUNNING";
    private static final String RECOVERY_QUERY = """
            SELECT
                run.id AS runId,
                run.user_id AS userId,
                run.started_at AS startedAt,
                run.execution_token AS executionToken
            FROM agent_run run
            WHERE run.deleted = 0
              AND run.agent_type = ?
              AND run.status = ?
              AND run.execution_token IS NOT NULL
              AND run.execution_token <> ''
              AND run.started_at IS NOT NULL
              AND run.started_at <= ?
              AND EXISTS (
                  SELECT 1
                  FROM async_task task
                  WHERE task.deleted = 0
                    AND task.biz_type = ?
                    AND task.biz_id = CAST(run.id AS CHAR)
              )
            ORDER BY run.started_at ASC
            LIMIT ?
            """;
    private static final String RECOVERY_UPDATE = """
            UPDATE agent_run
            SET status = 'FAILED',
                error_code = '%s',
                error_message = ?,
                duration_ms = ?,
                finished_at = ?,
                updated_at = ?
            WHERE id = ?
              AND user_id = ?
              AND deleted = 0
              AND status = 'RUNNING'
              AND execution_token = ?
            """.formatted(AgentErrorCode.RUN_TIMEOUT);
    private static final String RECOVERY_ASYNC_TASK_UPDATE = """
            UPDATE async_task
            SET status = 'FAILED',
                failure_reason = ?,
                completed_at = ?,
                updated_at = ?
            WHERE deleted = 0
              AND biz_type = ?
              AND biz_id = ?
              AND status = 'RUNNING'
            """;
    private static final String TIMEOUT_MESSAGE = "计划生成超时，请重新生成今日计划。";

    private final JdbcTemplate jdbcTemplate;
    private final DistributedLockHelper distributedLockHelper;

    @Value("${codecoachai.agent.daily-plan.timeout-recovery.enabled:true}")
    private boolean enabled;

    @Value("${codecoachai.agent.daily-plan.timeout-recovery.stale-minutes:15}")
    private long staleMinutes;

    @Value("${codecoachai.agent.daily-plan.timeout-recovery.scan-limit:50}")
    private int scanLimit;

    @Value("${codecoachai.agent.daily-plan.timeout-recovery.lock-wait-seconds:0}")
    private long lockWaitSeconds;

    @Value("${codecoachai.agent.daily-plan.timeout-recovery.lock-lease-seconds:300}")
    private long lockLeaseSeconds;

    @Scheduled(cron = "${codecoachai.agent.daily-plan.timeout-recovery.cron:0 */2 * * * ?}")
    public void recoverTimedOutRuns() {
        if (!enabled) {
            return;
        }
        try {
            boolean acquired = distributedLockHelper.tryLockAndRun(
                    TIMEOUT_RECOVERY_LOCK_KEY,
                    lockWaitSeconds,
                    lockLeaseSeconds,
                    this::recoverCandidates);
            if (!acquired) {
                log.debug("Agent daily plan timeout recovery skipped because another run is active");
            }
        } catch (Exception ex) {
            log.error("Agent daily plan timeout recovery failed", ex);
        }
    }

    private void recoverCandidates() {
        if (staleMinutes <= 0) {
            log.warn("Skip agent daily plan timeout recovery because staleMinutes must be positive: {}", staleMinutes);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleMinutes);
        Object[] args = {
                AGENT_TYPE,
                RUNNING_STATUS,
                Timestamp.valueOf(cutoff),
                AgentMqDispatcher.BIZ_TYPE_DAILY_PLAN_GENERATE,
                Math.max(scanLimit, 1)
        };
        int[] argTypes = {
                Types.VARCHAR,
                Types.VARCHAR,
                Types.TIMESTAMP,
                Types.VARCHAR,
                Types.INTEGER
        };
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(RECOVERY_QUERY, args, argTypes);
        for (Map<String, Object> row : rows) {
            recoverCandidate(row);
        }
    }

    private void recoverCandidate(Map<String, Object> row) {
        Long runId = toLong(row.get("runId"));
        Long userId = toLong(row.get("userId"));
        LocalDateTime startedAt = toLocalDateTime(row.get("startedAt"));
        String executionToken = trimToNull(row.get("executionToken"));
        if (runId == null || userId == null || startedAt == null || !StringUtils.hasText(executionToken)) {
            log.warn("Skip agent daily plan timeout recovery row because required fields are missing: {}",
                    safeRowSummary(row));
            return;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = Math.max(Duration.between(startedAt, finishedAt).toMillis(), 0L);
        Object[] args = {
                TIMEOUT_MESSAGE,
                durationMs,
                Timestamp.valueOf(finishedAt),
                Timestamp.valueOf(finishedAt),
                runId,
                userId,
                executionToken
        };
        int[] argTypes = {
                Types.VARCHAR,
                Types.BIGINT,
                Types.TIMESTAMP,
                Types.TIMESTAMP,
                Types.BIGINT,
                Types.BIGINT,
                Types.VARCHAR
        };
        int updated = jdbcTemplate.update(RECOVERY_UPDATE, args, argTypes);
        if (updated > 0) {
            int asyncTaskUpdated = markAsyncTasksFailed(runId, finishedAt);
            log.info("Recovered timed out agent daily plan runId={} userId={} asyncTaskRows={}",
                    runId, userId, asyncTaskUpdated);
            return;
        }
        log.debug("Skip agent daily plan timeout recovery because run changed runId={} userId={}", runId, userId);
    }

    private int markAsyncTasksFailed(Long runId, LocalDateTime finishedAt) {
        Object[] args = {
                TIMEOUT_MESSAGE,
                Timestamp.valueOf(finishedAt),
                Timestamp.valueOf(finishedAt),
                AgentMqDispatcher.BIZ_TYPE_DAILY_PLAN_GENERATE,
                String.valueOf(runId)
        };
        int[] argTypes = {
                Types.VARCHAR,
                Types.TIMESTAMP,
                Types.TIMESTAMP,
                Types.VARCHAR,
                Types.VARCHAR
        };
        return jdbcTemplate.update(RECOVERY_ASYNC_TASK_UPDATE, args, argTypes);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> safeRowSummary(Map<String, Object> row) {
        if (row == null) {
            return Map.of("fieldCount", 0);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fieldCount", row.size());
        summary.put("fields", row.keySet());
        summary.put("runId", toLong(row.get("runId")));
        summary.put("userId", toLong(row.get("userId")));
        summary.put("startedAtPresent", row.get("startedAt") != null);
        addTextMeta(summary, "executionToken", row.get("executionToken"));
        summary.put("rowHash", shortHash(row.keySet() + "|"
                + toLong(row.get("runId")) + "|"
                + toLong(row.get("userId")) + "|"
                + textMeta(row.get("executionToken"))));
        return summary;
    }

    private void addTextMeta(Map<String, Object> summary, String field, Object value) {
        String text = trimToNull(value);
        summary.put(field + "Length", text == null ? 0 : text.length());
        summary.put(field + "Hash", shortHash(text));
    }

    private String textMeta(Object value) {
        String text = trimToNull(value);
        return text == null ? "empty" : text.length() + ":" + shortHash(text);
    }

    private String shortHash(String value) {
        String hash = TextFingerprintUtils.sha256Hex(value);
        return hash == null ? null : hash.substring(0, Math.min(hash.length(), 12));
    }
}
