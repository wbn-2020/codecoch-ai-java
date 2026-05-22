package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionRunDTO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionResultVO;
import com.codecoachai.ai.agent.security.V4AdminPermissionGuard;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.Result;
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

    @GetMapping("/cases")
    public Result<List<PromptRegressionCaseVO>> cases(@RequestParam(required = false) String promptType,
                                                      @RequestParam(required = false) Integer enabled) {
        permissionGuard.require("admin:agent:prompt-regression:list");
        return Result.success(agentV4OpsService.listPromptCases(promptType, enabled));
    }

    @PostMapping("/cases")
    public Result<PromptRegressionCaseVO> createCase(@RequestBody PromptRegressionCaseSaveDTO dto) {
        permissionGuard.require("admin:agent:prompt-regression:write");
        return Result.success(agentV4OpsService.savePromptCase(dto));
    }

    @PutMapping("/cases/{id}")
    public Result<PromptRegressionCaseVO> updateCase(@PathVariable Long id,
                                                     @RequestBody PromptRegressionCaseSaveDTO dto) {
        permissionGuard.require("admin:agent:prompt-regression:write");
        dto.setId(id);
        return Result.success(agentV4OpsService.savePromptCase(dto));
    }

    @PostMapping("/cases/{id}/run")
    public Result<PromptRegressionResultVO> runCase(@PathVariable Long id,
                                                    @RequestBody(required = false) PromptRegressionRunDTO dto) {
        permissionGuard.require("admin:agent:prompt-regression:run");
        Long promptVersionId = dto == null ? null : dto.getPromptVersionId();
        return Result.success(agentV4OpsService.runPromptCase(id, promptVersionId));
    }

    @GetMapping("/results")
    public Result<List<PromptRegressionResultVO>> results(@RequestParam(required = false) Long caseId) {
        permissionGuard.require("admin:agent:prompt-regression:list");
        return Result.success(agentV4OpsService.listPromptResults(caseId));
    }
}
