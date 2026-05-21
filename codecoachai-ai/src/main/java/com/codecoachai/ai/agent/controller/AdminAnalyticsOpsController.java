package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsJobLogVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsMetricDefinitionVO;
import com.codecoachai.ai.agent.service.AgentAnalyticsService;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/analytics")
public class AdminAnalyticsOpsController {

    private final AgentV4OpsService agentV4OpsService;
    private final AgentAnalyticsService agentAnalyticsService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Integer days) {
        SecurityAssert.requireAdmin();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agent", agentAnalyticsService.adminAgentOverview(days));
        data.put("ai", agentAnalyticsService.adminAiOverview(days));
        data.put("feedback", agentV4OpsService.feedbackStats(days));
        return Result.success(data);
    }

    @GetMapping("/training")
    public Result<Map<String, Object>> training(@RequestParam(required = false) Integer days) {
        SecurityAssert.requireAdmin();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskStats", agentAnalyticsService.adminAgentTasks(days));
        data.put("agentTrend", agentAnalyticsService.adminAgentTrend(days));
        return Result.success(data);
    }

    @GetMapping("/metrics")
    public Result<List<AnalyticsMetricDefinitionVO>> metrics(@RequestParam(required = false) String category,
                                                             @RequestParam(required = false) Integer enabled) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.listMetrics(category, enabled));
    }

    @PostMapping("/metrics")
    public Result<AnalyticsMetricDefinitionVO> createMetric(@RequestBody AdminAnalyticsMetricSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.saveMetric(dto));
    }

    @PutMapping("/metrics/{id}")
    public Result<AnalyticsMetricDefinitionVO> updateMetric(@PathVariable Long id,
                                                            @RequestBody AdminAnalyticsMetricSaveDTO dto) {
        SecurityAssert.requireAdmin();
        dto.setId(id);
        return Result.success(agentV4OpsService.saveMetric(dto));
    }

    @GetMapping("/jobs")
    public Result<PageResult<AnalyticsJobLogVO>> jobs(@RequestParam(required = false) String jobCode,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long pageNo,
                                                      @RequestParam(required = false) Long pageSize) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.pageJobs(jobCode, status, pageNo, pageSize));
    }

    @PostMapping("/jobs/{id}/rerun")
    public Result<AnalyticsJobLogVO> rerun(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.rerunJob(id));
    }

    @PostMapping("/jobs/agent-daily-plan/run")
    public Result<AnalyticsJobLogVO> runDailyPlan(@RequestBody(required = false) AnalyticsJobRunDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.runDailyPlanBatch(dto));
    }

    @GetMapping("/agent/feedback")
    public Result<AgentFeedbackStatsVO> agentFeedback(@RequestParam(required = false) Integer days) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.feedbackStats(days));
    }
}
