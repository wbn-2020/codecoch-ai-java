package com.codecoachai.ai.agent.task;

import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.redis.lock.DistributedLockHelper;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDailyPlanScheduleTask {

    private static final String DAILY_PLAN_BATCH_LOCK_KEY_PREFIX = "codecoachai:lock:agent:daily-plan:batch:";

    private final AgentV4OpsService agentV4OpsService;
    private final DistributedLockHelper distributedLockHelper;

    @Value("${codecoachai.agent.daily-plan.enabled:true}")
    private boolean enabled;

    @Value("${codecoachai.agent.daily-plan.lock-wait-seconds:0}")
    private long lockWaitSeconds;

    @Value("${codecoachai.agent.daily-plan.lock-lease-seconds:900}")
    private long lockLeaseSeconds;

    @Value("${codecoachai.agent.daily-plan.batch-user-limit:100}")
    private int batchUserLimit;

    @Scheduled(cron = "${codecoachai.agent.daily-plan.cron:0 15 6 * * ?}")
    public void runDailyPlanBatch() {
        if (!enabled) {
            return;
        }
        try {
            // 定时任务不绑定单个用户，由 service 侧按规则批量生成每日 agent 计划。
            LocalDate planDate = LocalDate.now();
            boolean acquired = distributedLockHelper.tryLockAndRun(
                    DAILY_PLAN_BATCH_LOCK_KEY_PREFIX + planDate,
                    lockWaitSeconds,
                    lockLeaseSeconds,
                    () -> agentV4OpsService.runDailyPlanBatch(buildScheduledRequest(planDate)));
            if (!acquired) {
                log.info("V4 agent daily plan batch skipped because another run is active, planDate={}", planDate);
            }
        } catch (Exception ex) {
            // 定时调度失败只记录错误，避免异常向外抛出导致调度线程中断。
            log.error("V4 agent daily plan batch failed", ex);
        }
    }

    private AnalyticsJobRunDTO buildScheduledRequest(LocalDate planDate) {
        AnalyticsJobRunDTO dto = new AnalyticsJobRunDTO();
        dto.setStatDate(planDate);
        dto.setUserLimit(batchUserLimit);
        dto.setReason("scheduled daily plan batch");
        return dto;
    }
}
