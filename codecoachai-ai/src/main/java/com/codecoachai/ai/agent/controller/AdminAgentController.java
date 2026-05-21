package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AdminAgentRunQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AdminAgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent")
public class AdminAgentController {

    private final JobCoachAgentService jobCoachAgentService;

    @GetMapping("/runs")
    public Result<PageResult<AgentRunDetailVO>> pageRuns(@ModelAttribute AdminAgentRunQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(jobCoachAgentService.adminPageRuns(query));
    }

    @GetMapping("/runs/{id}")
    public Result<AgentRunDetailVO> runDetail(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(jobCoachAgentService.adminGetRunDetail(id));
    }

    @GetMapping("/tasks")
    public Result<PageResult<AgentTaskVO>> pageTasks(@ModelAttribute AdminAgentTaskQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(jobCoachAgentService.adminPageTasks(query));
    }
}
