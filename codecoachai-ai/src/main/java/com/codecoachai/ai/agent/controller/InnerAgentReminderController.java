package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.vo.AgentReminderCandidateVO;
import com.codecoachai.ai.agent.service.AgentReminderService;
import com.codecoachai.common.core.domain.Result;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/agent/reminders")
public class InnerAgentReminderController {

    private final AgentReminderService agentReminderService;

    @GetMapping("/candidates")
    public Result<List<AgentReminderCandidateVO>> listCandidates(@RequestParam("userId") Long userId,
                                                                 @RequestParam(value = "planDate", required = false)
                                                                 LocalDate planDate) {
        return Result.success(agentReminderService.listCandidates(userId, planDate));
    }
}
