package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AdminAnalyticsMetricSaveDTO;
import com.codecoachai.ai.agent.domain.dto.AnalyticsJobRunDTO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackStatsVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsJobLogVO;
import com.codecoachai.ai.agent.domain.vo.ops.AnalyticsMetricDefinitionVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentAnalyticsService;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
    private final V4AdminPermissionGuard permissionGuard;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:agent");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agent", agentAnalyticsService.adminAgentOverview(days));
        data.put("ai", agentAnalyticsService.adminAiOverview(days));
        data.put("feedback", safeFeedbackStats(days));
        return Result.success(data);
    }

    @GetMapping("/training")
    public Result<Map<String, Object>> training(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:agent");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskStats", agentAnalyticsService.adminAgentTasks(days));
        data.put("agentTrend", agentAnalyticsService.adminAgentTrend(days));
        return Result.success(data);
    }

    @GetMapping("/metrics")
    public Result<PageResult<AnalyticsMetricDefinitionVO>> metrics(@RequestParam(required = false) String category,
                                                                   @RequestParam(required = false) Integer enabled,
                                                                   @RequestParam(required = false) String keyword,
                                                                   @RequestParam(required = false) Long pageNo,
                                                                   @RequestParam(required = false) Long pageSize) {
        permissionGuard.require("admin:analytics:agent");
        long actualPageNo = pageNo(pageNo);
        long actualPageSize = pageSize(pageSize);
        try {
            List<AnalyticsMetricDefinitionVO> all = agentV4OpsService.listMetrics(category, enabled).stream()
                    .filter(item -> !StringUtils.hasText(keyword)
                            || containsIgnoreCase(item.getMetricCode(), keyword)
                            || containsIgnoreCase(item.getMetricName(), keyword)
                            || containsIgnoreCase(item.getDefinition(), keyword))
                    .toList();
            int fromIndex = (int) Math.min((actualPageNo - 1) * actualPageSize, all.size());
            int toIndex = (int) Math.min(fromIndex + actualPageSize, all.size());
            return Result.success(PageResult.of(all.subList(fromIndex, toIndex), all.size(), actualPageNo, actualPageSize));
        } catch (RuntimeException ex) {
            return Result.success(PageResult.empty(actualPageNo, actualPageSize));
        }
    }

    @PostMapping("/metrics")
    public Result<AnalyticsMetricDefinitionVO> createMetric(@RequestBody AdminAnalyticsMetricSaveDTO dto) {
        permissionGuard.require("admin:analytics:metric:write");
        return Result.success(agentV4OpsService.saveMetric(dto));
    }

    @PutMapping("/metrics/{id}")
    public Result<AnalyticsMetricDefinitionVO> updateMetric(@PathVariable Long id,
                                                            @RequestBody AdminAnalyticsMetricSaveDTO dto) {
        permissionGuard.require("admin:analytics:metric:write");
        dto.setId(id);
        return Result.success(agentV4OpsService.saveMetric(dto));
    }

    @GetMapping("/jobs")
    public Result<PageResult<AnalyticsJobLogVO>> jobs(@RequestParam(required = false) String jobCode,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long pageNo,
                                                      @RequestParam(required = false) Long pageSize) {
        permissionGuard.require("admin:analytics:agent");
        try {
            return Result.success(agentV4OpsService.pageJobs(jobCode, status, pageNo, pageSize));
        } catch (RuntimeException ex) {
            return Result.success(PageResult.empty(pageNo(pageNo), pageSize(pageSize)));
        }
    }

    @PostMapping("/jobs/{id}/rerun")
    public Result<AnalyticsJobLogVO> rerun(@PathVariable Long id) {
        permissionGuard.require("admin:analytics:job:run");
        return Result.success(agentV4OpsService.rerunJob(id));
    }

    @PostMapping("/jobs/agent-daily-plan/run")
    public Result<AnalyticsJobLogVO> runDailyPlan(@RequestBody(required = false) AnalyticsJobRunDTO dto) {
        permissionGuard.require("admin:analytics:job:run");
        return Result.success(agentV4OpsService.runDailyPlanBatch(dto));
    }

    @GetMapping("/agent/feedback")
    public Result<AgentFeedbackStatsVO> agentFeedback(@RequestParam(required = false) Integer days) {
        permissionGuard.require("admin:analytics:agent");
        return Result.success(safeFeedbackStats(days));
    }

    private AgentFeedbackStatsVO safeFeedbackStats(Integer days) {
        try {
            return agentV4OpsService.feedbackStats(days);
        } catch (RuntimeException ex) {
            return new AgentFeedbackStatsVO();
        }
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(keyword)
                && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private long pageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long pageSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 10L : Math.min(pageSize, 100L);
    }
}
