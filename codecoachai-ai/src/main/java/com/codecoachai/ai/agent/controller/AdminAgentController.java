package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AdminAgentRunQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AdminAgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
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
    private final V4AdminPermissionGuard permissionGuard;

    @GetMapping("/runs")
    public Result<PageResult<AgentRunDetailVO>> pageRuns(@ModelAttribute AdminAgentRunQueryDTO query) {
        permissionGuard.require("admin:agent:run:list");
        return Result.success(jobCoachAgentService.adminPageRuns(query));
    }

    @GetMapping("/runs/{id}")
    public Result<AgentRunDetailVO> runDetail(@PathVariable Long id) {
        permissionGuard.require("admin:agent:run:list");
        return Result.success(jobCoachAgentService.adminGetRunDetail(id));
    }

    @GetMapping("/tasks")
    public Result<PageResult<AgentTaskVO>> pageTasks(@ModelAttribute AdminAgentTaskQueryDTO query) {
        permissionGuard.require("admin:agent:task:list");
        return Result.success(jobCoachAgentService.adminPageTasks(query));
    }
}
