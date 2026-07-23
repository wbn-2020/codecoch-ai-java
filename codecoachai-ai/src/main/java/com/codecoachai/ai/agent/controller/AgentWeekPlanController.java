package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AgentWeekPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentPlanAdjustmentVO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentPlanInfluenceVO;
import com.codecoachai.ai.agent.domain.vo.weekplan.AgentWeekPlanVO;
import com.codecoachai.ai.agent.service.AgentWeekPlanService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/week-plan")
public class AgentWeekPlanController {

    private final AgentWeekPlanService agentWeekPlanService;

    @GetMapping("/current")
    public Result<AgentWeekPlanVO> current(@RequestParam(required = false) Long targetJobId,
                                           @RequestParam(required = false) LocalDate date) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentWeekPlanService.current(userId, targetJobId, date));
    }

    @PostMapping("/generate")
    @OperationLog(module = "agent", action = "GENERATE_AGENT_WEEK_PLAN",
            description = "Generate Agent week plan", logArgs = false, logResponse = false)
    public Result<AgentWeekPlanVO> generate(@RequestBody(required = false) AgentWeekPlanGenerateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentWeekPlanService.generate(userId, dto));
    }

    @PostMapping("/{id}/refresh")
    @OperationLog(module = "agent", action = "REFRESH_AGENT_WEEK_PLAN",
            description = "Refresh Agent week plan", logArgs = false, logResponse = false)
    public Result<AgentWeekPlanVO> refresh(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentWeekPlanService.refresh(userId, id));
    }

    @GetMapping("/{id}")
    public Result<AgentWeekPlanVO> detail(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentWeekPlanService.detail(userId, id));
    }

    @GetMapping("/{id}/adjustments")
    public Result<List<AgentPlanAdjustmentVO>> adjustments(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentWeekPlanService.adjustments(userId, id));
    }

    @GetMapping("/{id}/influences")
    public Result<List<AgentPlanInfluenceVO>> influences(@PathVariable Long id) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentWeekPlanService.influences(userId, id));
    }
}
