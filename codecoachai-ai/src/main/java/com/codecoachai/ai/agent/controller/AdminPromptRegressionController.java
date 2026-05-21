package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.domain.dto.PromptRegressionCaseSaveDTO;
import com.codecoachai.ai.agent.domain.dto.PromptRegressionRunDTO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionCaseVO;
import com.codecoachai.ai.agent.domain.vo.ops.PromptRegressionResultVO;
import com.codecoachai.ai.agent.service.AgentV4OpsService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
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

    @GetMapping("/cases")
    public Result<List<PromptRegressionCaseVO>> cases(@RequestParam(required = false) String promptType,
                                                      @RequestParam(required = false) Integer enabled) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.listPromptCases(promptType, enabled));
    }

    @PostMapping("/cases")
    public Result<PromptRegressionCaseVO> createCase(@RequestBody PromptRegressionCaseSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.savePromptCase(dto));
    }

    @PutMapping("/cases/{id}")
    public Result<PromptRegressionCaseVO> updateCase(@PathVariable Long id,
                                                     @RequestBody PromptRegressionCaseSaveDTO dto) {
        SecurityAssert.requireAdmin();
        dto.setId(id);
        return Result.success(agentV4OpsService.savePromptCase(dto));
    }

    @PostMapping("/cases/{id}/run")
    public Result<PromptRegressionResultVO> runCase(@PathVariable Long id,
                                                    @RequestBody(required = false) PromptRegressionRunDTO dto) {
        SecurityAssert.requireAdmin();
        Long promptVersionId = dto == null ? null : dto.getPromptVersionId();
        return Result.success(agentV4OpsService.runPromptCase(id, promptVersionId));
    }

    @GetMapping("/results")
    public Result<List<PromptRegressionResultVO>> results(@RequestParam(required = false) Long caseId) {
        SecurityAssert.requireAdmin();
        return Result.success(agentV4OpsService.listPromptResults(caseId));
    }
}
