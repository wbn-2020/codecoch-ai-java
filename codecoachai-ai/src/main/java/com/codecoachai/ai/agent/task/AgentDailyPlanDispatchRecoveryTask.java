package com.codecoachai.ai.agent.task;

import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.mq.AgentMqDispatcher;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class AgentDailyPlanDispatchRecoveryTask {

    private static final String DISPATCH_RECOVERY_LOCK_KEY = "codecoachai:lock:agent:daily-plan:dispatch-recovery";
    private static final String AGENT_TYPE = "JOB_COACH";
    private static final String RUNNING_STATUS = "RUNNING";
    private static final String RECOVERY_QUERY = """
            SELECT
                run.id AS runId,
                run.user_id AS userId,
                run.target_job_id AS targetJobId,
                run.plan_date AS planDate,
                run.execution_token AS executionToken
            FROM agent_run run
            WHERE run.deleted = 0
              AND run.agent_type = ?
              AND run.status = ?
              AND run.execution_token IS NOT NULL
              AND run.execution_token <> ''
              AND run.started_at IS NOT NULL
              AND run.started_at <= ?
              AND (run.updated_at IS NULL OR run.updated_at <= ?)
              AND run.input_snapshot_json IS NULL
              AND run.prompt_type IS NULL
              AND run.prompt_version_id IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM async_task task
                  WHERE task.deleted = 0
                    AND task.biz_type = ?
                    AND task.biz_id = CAST(run.id AS CHAR)
              )
            ORDER BY run.started_at ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JobCoachAgentService jobCoachAgentService;
    private final DistributedLockHelper distributedLockHelper;

    @Value("${codecoachai.agent.daily-plan.dispatch-recovery.enabled:true}")
    private boolean enabled;

    @Value("${codecoachai.agent.daily-plan.dispatch-recovery.grace-minutes:2}")
    private long graceMinutes;

    @Value("${codecoachai.agent.daily-plan.dispatch-recovery.scan-limit:50}")
    private int scanLimit;

    @Value("${codecoachai.agent.daily-plan.dispatch-recovery.lock-wait-seconds:0}")
    private long lockWaitSeconds;

    @Value("${codecoachai.agent.daily-plan.dispatch-recovery.lock-lease-seconds:300}")
    private long lockLeaseSeconds;

    @Scheduled(cron = "${codecoachai.agent.daily-plan.dispatch-recovery.cron:0 */2 * * * ?}")
    public void recoverMissingDispatches() {
        if (!enabled) {
            return;
        }
        try {
            boolean acquired = distributedLockHelper.tryLockAndRun(
                    DISPATCH_RECOVERY_LOCK_KEY,
                    lockWaitSeconds,
                    lockLeaseSeconds,
                    this::recoverCandidates);
            if (!acquired) {
                log.debug("Agent daily plan dispatch recovery skipped because another run is active");
            }
        } catch (Exception ex) {
            log.error("Agent daily plan dispatch recovery failed", ex);
        }
    }

    private void recoverCandidates() {
        if (graceMinutes <= 0) {
            log.warn("Skip agent daily plan dispatch recovery because graceMinutes must be positive: {}", graceMinutes);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(graceMinutes);
        Object[] args = {
                AGENT_TYPE,
                RUNNING_STATUS,
                Timestamp.valueOf(cutoff),
                Timestamp.valueOf(cutoff),
                AgentMqDispatcher.BIZ_TYPE_DAILY_PLAN_GENERATE,
                Math.max(scanLimit, 1)
        };
        int[] argTypes = {
                Types.VARCHAR,
                Types.VARCHAR,
                Types.TIMESTAMP,
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
        String executionToken = trimToNull(row.get("executionToken"));
        if (runId == null || userId == null || !StringUtils.hasText(executionToken)) {
            log.warn("Skip agent daily plan dispatch recovery row because required fields are missing: {}", row);
            return;
        }
        DailyPlanGenerateDTO dto = new DailyPlanGenerateDTO();
        dto.setDate(toLocalDate(row.get("planDate")));
        dto.setTargetJobId(toLong(row.get("targetJobId")));
        dto.setExecutionToken(executionToken);
        try {
            jobCoachAgentService.executeDailyPlan(userId, runId, dto);
            log.info("Recovered missing agent daily plan dispatch runId={} userId={}", runId, userId);
        } catch (Exception ex) {
            log.error("Recovering missing agent daily plan dispatch failed runId={} userId={}", runId, userId, ex);
        }
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

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return LocalDate.parse(text.trim());
            } catch (RuntimeException ex) {
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
