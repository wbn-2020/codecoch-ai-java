package com.codecoachai.ai.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codecoachai.ai.agent.domain.context.DailyPlanResult;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class AgentRunRetentionCleanupTask {

    private static final String RETENTION_CLEANUP_LOCK_KEY = "codecoachai:lock:agent:run:retention-cleanup";
    private static final String FIND_EXPIRED_RUNS = """
            SELECT
                run.id AS runId,
                run.output_json AS outputJson
            FROM agent_run run
            WHERE run.deleted = 0
              AND run.status IN ('SUCCESS', 'FAILED', 'CANCELED')
              AND run.finished_at IS NOT NULL
              AND run.finished_at <= ?
              AND (
                  (run.input_snapshot_json IS NOT NULL AND run.input_snapshot_json <> '')
                  OR (run.output_json IS NOT NULL AND run.output_json <> '')
                  OR (run.raw_output_text IS NOT NULL AND run.raw_output_text <> '')
              )
            ORDER BY run.finished_at ASC
            LIMIT ?
            """;
    private static final String SCRUB_DIAGNOSTICS = """
            UPDATE agent_run
            SET input_snapshot_json = NULL,
                output_json = ?,
                raw_output_text = NULL,
                updated_at = ?
            WHERE deleted = 0
              AND id = ?
              AND status IN ('SUCCESS', 'FAILED', 'CANCELED')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DistributedLockHelper distributedLockHelper;
    private final ObjectMapper objectMapper;

    @Value("${codecoachai.agent.run.retention-cleanup.enabled:true}")
    private boolean enabled;

    @Value("${codecoachai.agent.run.retention-cleanup.retention-days:30}")
    private long retentionDays;

    @Value("${codecoachai.agent.run.retention-cleanup.scan-limit:100}")
    private int scanLimit;

    @Value("${codecoachai.agent.run.retention-cleanup.lock-wait-seconds:0}")
    private long lockWaitSeconds;

    @Value("${codecoachai.agent.run.retention-cleanup.lock-lease-seconds:300}")
    private long lockLeaseSeconds;

    @Scheduled(cron = "${codecoachai.agent.run.retention-cleanup.cron:0 25 3 * * ?}")
    public void cleanupExpiredDiagnostics() {
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
                log.debug("Agent run retention cleanup skipped because another run is active");
            }
        } catch (Exception ex) {
            log.error("Agent run retention cleanup failed", ex);
        }
    }

    private void cleanupCandidates() {
        if (retentionDays <= 0L) {
            log.warn("Skip agent_run retention scrub because retentionDays={} is not positive", retentionDays);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(retentionDays, 0L));
        Object[] args = {
                Timestamp.valueOf(cutoff),
                Math.max(scanLimit, 1)
        };
        int[] argTypes = {Types.TIMESTAMP, Types.INTEGER};
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(FIND_EXPIRED_RUNS, args, argTypes);
        for (Map<String, Object> row : rows) {
            cleanupCandidate(row);
        }
    }

    private void cleanupCandidate(Map<String, Object> row) {
        Long runId = toLong(row.get("runId"));
        if (runId == null) {
            log.warn("Skip agent run retention cleanup row because runId is missing: {}", row);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Object[] args = {
                minimizeOutputJson(trimToNull(row.get("outputJson"))),
                Timestamp.valueOf(now),
                runId
        };
        int[] argTypes = {Types.VARCHAR, Types.TIMESTAMP, Types.BIGINT};
        int updated = jdbcTemplate.update(SCRUB_DIAGNOSTICS, args, argTypes);
        if (updated > 0) {
            log.info("Scrubbed historical diagnostics for agent_run id={}", runId);
        }
    }

    private String minimizeOutputJson(String outputJson) {
        if (!StringUtils.hasText(outputJson)) {
            return null;
        }
        try {
            DailyPlanResult parsed = objectMapper.readValue(outputJson, DailyPlanResult.class);
            DailyPlanResult minimized = new DailyPlanResult();
            minimized.setSummary(parsed.getSummary());
            minimized.setFocusSkills(copyFocusSkills(parsed.getFocusSkills()));
            minimized.setTasks(new ArrayList<>());
            return objectMapper.writeValueAsString(minimized);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<DailyPlanResult.FocusSkill> copyFocusSkills(List<DailyPlanResult.FocusSkill> focusSkills) {
        List<DailyPlanResult.FocusSkill> copy = new ArrayList<>();
        if (focusSkills == null) {
            return copy;
        }
        for (DailyPlanResult.FocusSkill skill : focusSkills) {
            if (skill == null) {
                continue;
            }
            DailyPlanResult.FocusSkill item = new DailyPlanResult.FocusSkill();
            item.setCode(skill.getCode());
            item.setName(skill.getName());
            copy.add(item);
        }
        return copy;
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

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
