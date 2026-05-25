package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.AiCallLogQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionCreateDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionQueryDTO;
import com.codecoachai.ai.domain.dto.PromptVersionActionDTO;
import com.codecoachai.ai.domain.dto.PromptVersionTestDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateDetailVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.domain.vo.PromptVersionTestVO;
import com.codecoachai.ai.service.PromptTemplateService;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin AI", description = "AI 管理端接口")
public class AdminAiController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping("/admin/ai/prompts")
    public Result<PageResult<PromptTemplateVO>> pagePrompts(@RequestParam(required = false) Long pageNo,
                                                            @RequestParam(required = false) Long pageSize,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String scene,
                                                            @RequestParam(required = false) Integer status) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.pagePrompts(pageNo, pageSize, keyword, scene, status));
    }

    @GetMapping("/admin/ai/prompt-templates")
    public Result<PageResult<PromptTemplateVO>> pagePromptTemplates(PromptTemplateQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.pagePrompts(query));
    }

    @GetMapping("/admin/ai/prompts/page")
    public Result<PageResult<PromptTemplateVO>> pagePromptsAlias(@RequestParam(required = false) Long pageNo,
                                                                 @RequestParam(required = false) Long pageSize,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String scene,
                                                                 @RequestParam(required = false) Integer status) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.pagePrompts(pageNo, pageSize, keyword, scene, status));
    }

    @OperationLog(module = "ai", action = "CREATE_PROMPT", description = "新增 Prompt 模板")
    @PostMapping("/admin/ai/prompts")
    public Result<PromptTemplateVO> createPrompt(@Valid @RequestBody PromptTemplateSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.createPrompt(dto));
    }

    @GetMapping("/admin/ai/prompts/{id}")
    public Result<PromptTemplateVO> getPrompt(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.getPrompt(id));
    }

    @GetMapping("/admin/ai/prompt-templates/{id}")
    public Result<PromptTemplateDetailVO> getPromptTemplate(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.getPromptDetail(id));
    }

    @OperationLog(module = "ai", action = "UPDATE_PROMPT", description = "编辑 Prompt 模板")
    @PutMapping("/admin/ai/prompts/{id}")
    public Result<PromptTemplateVO> updatePrompt(@PathVariable Long id,
                                                 @Valid @RequestBody PromptTemplateSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.updatePrompt(id, dto));
    }

    @OperationLog(module = "ai", action = "DELETE_PROMPT", description = "删除 Prompt 模板")
    @DeleteMapping("/admin/ai/prompts/{id}")
    public Result<Void> deletePrompt(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        promptTemplateService.deletePrompt(id);
        return Result.success();
    }

    @OperationLog(module = "ai", action = "UPDATE_PROMPT_STATUS", description = "切换 Prompt 模板状态")
    @PutMapping("/admin/ai/prompts/{id}/status")
    public Result<Void> updatePromptStatus(@PathVariable Long id,
                                           @Valid @RequestBody UpdatePromptStatusDTO dto) {
        SecurityAssert.requireAdmin();
        promptTemplateService.updateStatus(id, dto);
        return Result.success();
    }

    @GetMapping("/admin/ai/prompt-templates/{id}/versions")
    public Result<PageResult<PromptTemplateVersionVO>> pagePromptVersions(@PathVariable Long id,
                                                                          PromptTemplateVersionQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.pageVersions(id, query));
    }

    @OperationLog(module = "ai", action = "CREATE_PROMPT_VERSION", description = "新增 Prompt 版本")
    @PostMapping("/admin/ai/prompt-templates/{id}/versions")
    public Result<PromptTemplateVersionVO> createPromptVersion(@PathVariable Long id,
                                                               @Valid @RequestBody PromptTemplateVersionCreateDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.createVersion(id, dto));
    }

    @OperationLog(module = "ai", action = "ACTIVATE_PROMPT_VERSION", description = "激活 Prompt 版本")
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/activate")
    @Operation(summary = "激活 Prompt 版本", description = "管理端接口：激活指定 Prompt 版本，并同步模板当前内容。")
    public Result<PromptTemplateVersionVO> activatePromptVersion(@PathVariable Long versionId,
                                                                 @RequestBody(required = false)
                                                                 PromptVersionActionDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.activateVersion(versionId, dto));
    }

    @OperationLog(module = "ai", action = "ROLLBACK_PROMPT_VERSION", description = "回滚 Prompt 版本")
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/rollback")
    @Operation(summary = "回滚 Prompt 版本", description = "管理端接口：等价于激活历史版本，内部复用激活逻辑并保留 changeLog。")
    public Result<PromptTemplateVersionVO> rollbackPromptVersion(@PathVariable Long versionId,
                                                                 @RequestBody(required = false)
                                                                 PromptVersionActionDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.rollbackVersion(versionId, dto));
    }

    @OperationLog(module = "ai", action = "DISABLE_PROMPT_VERSION", description = "禁用 Prompt 版本")
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/disable")
    @Operation(summary = "禁用 Prompt 版本", description = "管理端接口：禁用非激活状态的 Prompt 版本。")
    public Result<Void> disablePromptVersion(@PathVariable Long versionId,
                                             @RequestBody(required = false) PromptVersionActionDTO dto) {
        SecurityAssert.requireAdmin();
        promptTemplateService.disableVersion(versionId, dto);
        return Result.success();
    }

    @OperationLog(module = "ai", action = "TEST_PROMPT_VERSION", description = "测试 Prompt 版本", logResponse = false)
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/test")
    @Operation(summary = "测试 Prompt 版本", description = "管理端接口：渲染或调用 AI 测试指定 Prompt 版本。")
    public Result<PromptVersionTestVO> testPromptVersion(@PathVariable Long versionId,
                                                         @RequestBody(required = false) PromptVersionTestDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.testVersion(versionId, dto));
    }

    @GetMapping("/admin/ai/prompt-templates/{templateId}/call-logs")
    @Operation(summary = "查询模板调用记录", description = "管理端接口：按 Prompt 模板查询 AI 调用日志。")
    public Result<PageResult<AiCallLogVO>> pageTemplateCallLogs(@PathVariable Long templateId,
                                                                AiCallLogQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.pageTemplateLogs(templateId, query));
    }

    @GetMapping("/admin/ai/prompt-template-versions/{versionId}/call-logs")
    @Operation(summary = "查询版本调用记录", description = "管理端接口：按 Prompt 版本查询 AI 调用日志。")
    public Result<PageResult<AiCallLogVO>> pageVersionCallLogs(@PathVariable Long versionId,
                                                               AiCallLogQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.pageVersionLogs(versionId, query));
    }

    @GetMapping({"/admin/ai/call-logs", "/admin/ai/logs"})
    public Result<PageResult<AiCallLogVO>> pageLogs(AiCallLogQueryDTO query,
                                                    @RequestParam(required = false) Long pageNo,
                                                    @RequestParam(required = false) Long pageSize) {
        SecurityAssert.requireAdmin();
        if (pageNo != null) {
            query.setPageNo(pageNo);
        }
        if (pageSize != null) {
            query.setPageSize(pageSize);
        }
        return Result.success(promptTemplateService.pageLogs(query));
    }

    @GetMapping("/admin/ai/logs/page")
    public Result<PageResult<AiCallLogVO>> pageLogsAlias(AiCallLogQueryDTO query) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.pageLogs(query));
    }

    @GetMapping({"/admin/ai/call-logs/{id}", "/admin/ai/logs/{id}"})
    public Result<AiCallLogVO> getLog(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.getLog(id));
    }
}
