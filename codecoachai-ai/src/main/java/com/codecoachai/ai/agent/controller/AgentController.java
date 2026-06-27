package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AgentCoachActionDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AgentTaskSkipDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.vo.AgentCoachActionVO;
import com.codecoachai.ai.agent.domain.vo.AgentRunUserDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentController {

    private final JobCoachAgentService jobCoachAgentService;

    @PostMapping("/job-coach/daily-plan/generate")
    @OperationLog(module = "agent", action = "GENERATE_AGENT_DAILY_PLAN", description = "Generate JobCoachAgent daily plan", logResponse = false)
    public Result<DailyPlanVO> generateDailyPlan(@Valid @RequestBody(required = false) DailyPlanGenerateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.generateDailyPlan(userId, dto));
    }

    @GetMapping("/job-coach/daily-plan/latest")
    public Result<DailyPlanVO> latestDailyPlan(@RequestParam(required = false) Long targetJobId,
                                               @RequestParam(required = false) LocalDate date) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.latestDailyPlan(userId, targetJobId, date));
    }

    @GetMapping("/tasks/today")
    public Result<List<AgentTaskVO>> todayTasks(@RequestParam(required = false) Long targetJobId,
                                                @RequestParam(required = false) LocalDate date,
                                                @RequestParam(required = false) String status) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.todayTasks(userId, targetJobId, date, status));
    }

    @GetMapping("/tasks")
    public Result<PageResult<AgentTaskVO>> pageTasks(@ModelAttribute AgentTaskQueryDTO query) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.pageTasks(userId, query));
    }

    @PostMapping("/tasks/{id}/complete")
    @OperationLog(module = "agent", action = "COMPLETE_AGENT_TASK", description = "Complete Agent task")
    public Result<AgentTaskVO> completeTask(@PathVariable Long id,
                                            @RequestBody(required = false) AgentTaskCompleteDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.completeTask(userId, id, dto));
    }

    @PostMapping("/coach/contextual-actions")
    @OperationLog(module = "agent", action = "RUN_AGENT_CONTEXTUAL_COACH_ACTION", description = "Run contextual Agent coach action", logResponse = false)
    public Result<AgentCoachActionVO> performCoachAction(@Valid @RequestBody AgentCoachActionDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.performCoachAction(userId, dto));
    }

    @PostMapping("/tasks/{id}/start")
    @OperationLog(module = "agent", action = "START_AGENT_TASK", description = "Start Agent task")
    public Result<AgentTaskVO> startTask(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.startTask(userId, id));
    }

    @PostMapping("/tasks/{id}/skip")
    @OperationLog(module = "agent", action = "SKIP_AGENT_TASK", description = "Skip Agent task")
    public Result<AgentTaskVO> skipTask(@PathVariable Long id,
                                        @RequestBody(required = false) AgentTaskSkipDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.skipTask(userId, id, dto));
    }

    @PostMapping("/tasks/{id}/restore")
    @OperationLog(module = "agent", action = "RESTORE_AGENT_TASK", description = "Restore Agent task")
    public Result<AgentTaskVO> restoreTask(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.restoreTask(userId, id));
    }

    @GetMapping("/runs/{id}")
    public Result<AgentRunUserDetailVO> runDetail(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(jobCoachAgentService.getRunDetail(userId, id));
    }
}
