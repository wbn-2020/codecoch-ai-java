package com.codecoachai.ai.agent.task;

import com.codecoachai.ai.agent.service.AgentV4OpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDailyPlanScheduleTask {

    private final AgentV4OpsService agentV4OpsService;

    @Value("${codecoachai.agent.daily-plan.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${codecoachai.agent.daily-plan.cron:0 15 6 * * ?}")
    public void runDailyPlanBatch() {
        if (!enabled) {
            return;
        }
        try {
            // 定时任务不绑定单个用户，由 service 侧按规则批量生成每日 agent 计划。
            agentV4OpsService.runDailyPlanBatch(null);
        } catch (Exception ex) {
            // 定时调度失败只记录错误，避免异常向外抛出导致调度线程中断。
            log.error("V4 agent daily plan batch failed", ex);
        }
    }
}
