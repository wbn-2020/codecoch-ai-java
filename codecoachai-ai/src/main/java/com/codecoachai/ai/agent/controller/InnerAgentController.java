package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AgentBusinessActionCompleteDTO;
import com.codecoachai.ai.agent.domain.dto.AgentRunFailureDTO;
import com.codecoachai.ai.agent.domain.dto.DailyPlanGenerateDTO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.domain.vo.DailyPlanVO;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/agent")
public class InnerAgentController {

    private final JobCoachAgentService jobCoachAgentService;

    @PostMapping("/job-coach/daily-plan/runs/{runId}/execute")
    public Result<DailyPlanVO> executeDailyPlan(@PathVariable("runId") Long runId,
                                                @RequestBody DailyPlanGenerateDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        return Result.success(jobCoachAgentService.executeDailyPlan(dto.getUserId(), runId, dto));
    }

    @PostMapping("/job-coach/daily-plan/runs/{runId}/fail")
    public Result<DailyPlanVO> failDailyPlan(@PathVariable("runId") Long runId,
                                             @RequestBody AgentRunFailureDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        return Result.success(jobCoachAgentService.failDailyPlanRun(dto.getUserId(), runId, dto));
    }

    @PostMapping("/job-coach/business-actions/complete")
    public Result<AgentTaskVO> completeBusinessAction(@RequestBody AgentBusinessActionCompleteDTO dto) {
        if (dto == null || dto.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userId is required");
        }
        return Result.success(jobCoachAgentService.completeBusinessAction(dto));
    }
}
