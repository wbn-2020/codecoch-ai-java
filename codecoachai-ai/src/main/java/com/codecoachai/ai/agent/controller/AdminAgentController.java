package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.AdminAgentRunQueryDTO;
import com.codecoachai.ai.agent.domain.dto.AdminAgentTaskQueryDTO;
import com.codecoachai.ai.agent.domain.vo.AgentRunDetailVO;
import com.codecoachai.ai.agent.domain.vo.AgentTaskVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.JobCoachAgentService;
import com.codecoachai.ai.domain.dto.AiLogRawAccessDTO;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.web.log.OperationLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/agent")
public class AdminAgentController {

    private static final String RAW_ACCESS_PERMISSION = "admin:ai:log:raw:view";

    private final JobCoachAgentService jobCoachAgentService;
    private final V4AdminPermissionGuard permissionGuard;

    @GetMapping("/runs")
    public Result<PageResult<AgentRunDetailVO>> pageRuns(@ModelAttribute AdminAgentRunQueryDTO query) {
        permissionGuard.require("admin:agent:run:list");
        PageResult<AgentRunDetailVO> result = jobCoachAgentService.adminPageRuns(query);
        if (result != null && result.getRecords() != null) {
            result.getRecords().forEach(item -> applyRawAccess(item, false));
        }
        return Result.success(result);
    }

    @GetMapping("/runs/{id}")
    public Result<AgentRunDetailVO> runDetail(@PathVariable Long id) {
        permissionGuard.require("admin:agent:run:list");
        AgentRunDetailVO detail = jobCoachAgentService.adminGetRunDetail(id);
        applyRawAccess(detail, false);
        return Result.success(detail);
    }

    @OperationLog(module = "agent", action = "VIEW_AGENT_RUN_RAW", description = "查看 Agent 运行诊断原文", logResponse = false)
    @PostMapping("/runs/{id}/raw")
    public Result<AgentRunDetailVO> runRaw(@PathVariable Long id,
                                           @Valid @RequestBody AiLogRawAccessDTO dto) {
        permissionGuard.require("admin:agent:run:list");
        permissionGuard.require(RAW_ACCESS_PERMISSION);
        validateRawAccess(dto.getAccessReason(), dto.isConfirmSensitiveAccess());
        AgentRunDetailVO detail = jobCoachAgentService.adminGetRunDetail(id);
        applyRawAccess(detail, true);
        return Result.success(detail);
    }

    @GetMapping("/tasks")
    public Result<PageResult<AgentTaskVO>> pageTasks(@ModelAttribute AdminAgentTaskQueryDTO query) {
        permissionGuard.require("admin:agent:task:list");
        return Result.success(jobCoachAgentService.adminPageTasks(query));
    }

    private void applyRawAccess(AgentRunDetailVO detail, boolean includeRaw) {
        if (detail == null) {
            return;
        }
        detail.setRawAccessPermission(RAW_ACCESS_PERMISSION);
        detail.setRawAvailable(hasRawDiagnostics(detail));
        if (!includeRaw) {
            detail.setInputSnapshotJson(null);
            detail.setOutputJson(null);
            detail.setRawOutputText(null);
        }
    }

    private boolean hasRawDiagnostics(AgentRunDetailVO detail) {
        return StringUtils.hasText(detail.getInputSnapshotJson())
                || StringUtils.hasText(detail.getOutputJson())
                || StringUtils.hasText(detail.getRawOutputText());
    }

    private void validateRawAccess(String accessReason, boolean confirmSensitiveAccess) {
        if (!confirmSensitiveAccess) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先确认本次敏感原文访问");
        }
        if (!StringUtils.hasText(accessReason)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写本次查看 AI 原文的访问原因");
        }
        if (accessReason.trim().length() > 300) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "访问原因不能超过 300 个字符");
        }
    }
}
