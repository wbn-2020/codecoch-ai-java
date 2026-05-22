package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AgentFeedbackCreateDTO;
import com.codecoachai.ai.agent.domain.vo.feedback.AgentFeedbackVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/feedback")
public class AgentFeedbackController {

    private final AgentV4OpsService agentV4OpsService;

    @PostMapping
    public Result<AgentFeedbackVO> create(@RequestBody AgentFeedbackCreateDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.createFeedback(userId, dto));
    }

    @GetMapping
    public Result<PageResult<AgentFeedbackVO>> page(Long agentTaskId, Long agentRunId, String feedbackType,
                                                    Long pageNo, Long pageSize) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(agentV4OpsService.pageFeedback(userId, agentTaskId, agentRunId, feedbackType, pageNo, pageSize));
    }
}
