package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionRunDTO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionResultVO;
import com.codecoachai.ai.agent.security.AdminOperationConfirmationGuard;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.web.log.OperationLog;
import java.util.List;
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
@RequestMapping("/admin/agent/prompt-regression")
public class AdminPromptRegressionController {

    private final AgentV4OpsService agentV4OpsService;
    private final V4AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/cases")
    public Result<List<PromptRegressionCaseVO>> cases(@RequestParam(required = false) String promptType,
                                                      @RequestParam(required = false) Integer enabled) {
        permissionGuard.require("admin:agent:prompt-regression:list");
        return Result.success(agentV4OpsService.listPromptCases(promptType, enabled));
    }

    @OperationLog(module = "ai", action = "CREATE_PROMPT_REGRESSION_CASE", description = "创建提示词回归用例", logArgs = false, logResponse = false)
    @PostMapping("/cases")
    public Result<PromptRegressionCaseVO> createCase(@RequestBody PromptRegressionCaseSaveDTO dto) {
        permissionGuard.require("admin:agent:prompt-regression:write");
        String lockKey = requireConfirmedCaseSave("create", null, dto);
        try {
            return Result.success(agentV4OpsService.savePromptCase(dto));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @OperationLog(module = "ai", action = "UPDATE_PROMPT_REGRESSION_CASE", description = "更新提示词回归用例", logArgs = false, logResponse = false)
    @PutMapping("/cases/{id}")
    public Result<PromptRegressionCaseVO> updateCase(@PathVariable Long id,
                                                     @RequestBody PromptRegressionCaseSaveDTO dto) {
        permissionGuard.require("admin:agent:prompt-regression:write");
        String lockKey = requireConfirmedCaseSave("update", id, dto);
        dto.setId(id);
        try {
            return Result.success(agentV4OpsService.savePromptCase(dto));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @OperationLog(module = "ai", action = "RUN_PROMPT_REGRESSION_CASE", description = "运行提示词回归用例", logArgs = false, logResponse = false)
    @PostMapping("/cases/{id}/run")
    public Result<PromptRegressionResultVO> runCase(@PathVariable Long id,
                                                     @RequestBody(required = false) PromptRegressionRunDTO dto) {
        permissionGuard.require("admin:agent:prompt-regression:run");
        Long promptVersionId = dto == null ? null : dto.getPromptVersionId();
        String lockKey = operationConfirmationGuard.requireConfirmed(
                "PROMPT_REGRESSION_RUN:" + id + ":" + promptVersionId,
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey());
        try {
            return Result.success(agentV4OpsService.runPromptCase(id, promptVersionId));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @GetMapping("/results")
    public Result<PageResult<PromptRegressionResultVO>> results(@RequestParam(required = false) Long caseId,
                                                                @RequestParam(defaultValue = "1") Long pageNo,
                                                                @RequestParam(defaultValue = "20") Long pageSize) {
        permissionGuard.require("admin:agent:prompt-regression:list");
        return Result.success(agentV4OpsService.pagePromptResults(caseId, pageNo, pageSize));
    }

    private String requireConfirmedCaseSave(String action, Long id, PromptRegressionCaseSaveDTO dto) {
        return operationConfirmationGuard.requireConfirmed(
                "PROMPT_REGRESSION_CASE_" + action.toUpperCase() + ":" + (id == null ? "new" : id),
                dto == null ? null : dto.getConfirm(),
                dto == null ? null : dto.getDryRun(),
                dto == null ? null : dto.getReason(),
                dto == null ? null : dto.getIdempotencyKey());
    }

    @GetMapping({"", "/"})
    public Result<PageResult<PromptRegressionCaseVO>> pageCases(@RequestParam(required = false) String promptType,
                                                                @RequestParam(required = false) Integer enabled,
                                                                @RequestParam(defaultValue = "1") Long pageNo,
                                                                @RequestParam(defaultValue = "10") Long pageSize) {
        permissionGuard.require("admin:agent:prompt-regression:list");
        return Result.success(agentV4OpsService.pagePromptCases(promptType, enabled, pageNo, pageSize));
    }
}
