package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.AiCallLogQueryDTO;
import com.codecoachai.ai.domain.dto.AiLogRawAccessDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateActionDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateQueryDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionCreateDTO;
import com.codecoachai.ai.domain.dto.PromptTemplateVersionQueryDTO;
import com.codecoachai.ai.domain.dto.PromptVersionActionDTO;
import com.codecoachai.ai.domain.dto.PromptVersionTestDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.agent.security.AdminOperationConfirmationGuard;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateDetailVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.domain.vo.PromptVersionTestVO;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.ai.service.PromptTemplateService;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.admin.AdminPermissionGuard;
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
import java.util.function.Supplier;

@RestController
@RequiredArgsConstructor
@Tag(name = "Admin AI", description = "AI 管理端接口")
public class AdminAiController {

    private static final String PERM_PROMPT_LIST = "admin:ai:prompt:list";
    private static final String PERM_PROMPT_WRITE = "admin:ai:prompt:write";
    private static final String PERM_PROMPT_PUBLISH = "admin:ai:prompt:publish";
    private static final String PERM_PROMPT_TEST = "admin:ai:prompt:test";
    private static final String PERM_PROMPT_RAW_VIEW = "admin:ai:prompt:raw:view";
    private static final String PERM_LOG_LIST = "admin:ai:log:list";
    private static final String PERM_LOG_RAW_VIEW = "admin:ai:log:raw:view";

    private final PromptTemplateService promptTemplateService;
    private final AdminPermissionGuard permissionGuard;
    private final AdminOperationConfirmationGuard operationConfirmationGuard;

    @GetMapping("/admin/ai/prompts")
    public Result<PageResult<PromptTemplateVO>> pagePrompts(@RequestParam(required = false) Long pageNo,
                                                            @RequestParam(required = false) Long pageSize,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String scene,
                                                            @RequestParam(required = false) Integer status) {
        permissionGuard.require(PERM_PROMPT_LIST);
        return Result.success(promptTemplateService.pagePrompts(pageNo, pageSize, keyword, scene, status));
    }

    @GetMapping("/admin/ai/prompt-templates")
    public Result<PageResult<PromptTemplateVO>> pagePromptTemplates(PromptTemplateQueryDTO query) {
        permissionGuard.require(PERM_PROMPT_LIST);
        return Result.success(promptTemplateService.pagePrompts(query));
    }

    @GetMapping("/admin/ai/prompts/page")
    public Result<PageResult<PromptTemplateVO>> pagePromptsAlias(@RequestParam(required = false) Long pageNo,
                                                                 @RequestParam(required = false) Long pageSize,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String scene,
                                                                 @RequestParam(required = false) Integer status) {
        permissionGuard.require(PERM_PROMPT_LIST);
        return Result.success(promptTemplateService.pagePrompts(pageNo, pageSize, keyword, scene, status));
    }

    @OperationLog(module = "ai", action = "CREATE_PROMPT", description = "新增 Prompt 模板", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/prompts")
    public Result<PromptTemplateVO> createPrompt(@Valid @RequestBody PromptTemplateSaveDTO dto) {
        permissionGuard.require(PERM_PROMPT_WRITE);
        return runConfirmedOperation("prompt-template-create:" + dto.getScene(),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> promptTemplateService.createPrompt(dto));
    }

    @GetMapping("/admin/ai/prompts/{id}")
    public Result<PromptTemplateVO> getPrompt(@PathVariable Long id) {
        permissionGuard.require(PERM_PROMPT_LIST);
        return Result.success(promptTemplateService.getPrompt(id));
    }

    @GetMapping("/admin/ai/prompt-templates/{id}")
    public Result<PromptTemplateDetailVO> getPromptTemplate(@PathVariable Long id) {
        permissionGuard.require(PERM_PROMPT_LIST);
        return Result.success(promptTemplateService.getPromptDetail(id));
    }

    @OperationLog(module = "ai", action = "VIEW_PROMPT_TEMPLATE_RAW", description = "查看 Prompt 模板原文", logArgs = true, logResponse = false)
    @PostMapping({"/admin/ai/prompts/{id}/raw", "/admin/ai/prompt-templates/{id}/raw"})
    public Result<PromptTemplateDetailVO> getPromptRaw(@PathVariable Long id,
                                                       @Valid @RequestBody AiLogRawAccessDTO dto) {
        permissionGuard.require(PERM_PROMPT_RAW_VIEW);
        String lockKey = requireRawAccess("prompt-template-raw:" + id, dto);
        try {
            return Result.success(promptTemplateService.getPromptRaw(id));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @GetMapping({"/admin/ai/prompts/{id}/raw", "/admin/ai/prompt-templates/{id}/raw"})
    public Result<PromptTemplateDetailVO> getPromptRawCompat(@PathVariable Long id) {
        permissionGuard.require(PERM_PROMPT_RAW_VIEW);
        throw new BusinessException(ErrorCode.PARAM_ERROR,
                "Prompt 原文需要使用 POST 请求，并填写访问原因。");
    }

    @OperationLog(module = "ai", action = "UPDATE_PROMPT", description = "编辑 Prompt 模板", logArgs = false, logResponse = false)
    @PutMapping("/admin/ai/prompts/{id}")
    public Result<PromptTemplateVO> updatePrompt(@PathVariable Long id,
                                                 @Valid @RequestBody PromptTemplateSaveDTO dto) {
        permissionGuard.require(PERM_PROMPT_WRITE);
        return runConfirmedOperation("prompt-template-update:" + id,
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> promptTemplateService.updatePrompt(id, dto));
    }

    @OperationLog(module = "ai", action = "DELETE_PROMPT", description = "删除 Prompt 模板", logArgs = false, logResponse = false)
    @DeleteMapping("/admin/ai/prompts/{id}")
    public Result<Void> deletePrompt(@PathVariable Long id,
                                     @RequestBody(required = false) PromptTemplateActionDTO dto) {
        permissionGuard.require(PERM_PROMPT_WRITE);
        PromptTemplateActionDTO action = dto == null ? new PromptTemplateActionDTO() : dto;
        return runConfirmedVoidOperation("prompt-template-delete:" + id,
                action.getConfirm(), action.getDryRun(), action.getReason(), action.getIdempotencyKey(),
                () -> promptTemplateService.deletePrompt(id, action));
    }

    @OperationLog(module = "ai", action = "UPDATE_PROMPT_STATUS", description = "切换 Prompt 模板状态", logArgs = false, logResponse = false)
    @PutMapping("/admin/ai/prompts/{id}/status")
    public Result<Void> updatePromptStatus(@PathVariable Long id,
                                           @Valid @RequestBody UpdatePromptStatusDTO dto) {
        permissionGuard.require(PERM_PROMPT_WRITE);
        return runConfirmedVoidOperation("prompt-template-status:" + id + ":" + dto.getStatus(),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> promptTemplateService.updateStatus(id, dto));
    }

    @GetMapping("/admin/ai/prompt-templates/{id}/versions")
    public Result<PageResult<PromptTemplateVersionVO>> pagePromptVersions(@PathVariable Long id,
                                                                          PromptTemplateVersionQueryDTO query) {
        permissionGuard.require(PERM_PROMPT_LIST);
        return Result.success(promptTemplateService.pageVersions(id, query));
    }

    @OperationLog(module = "ai", action = "VIEW_PROMPT_VERSION_RAW", description = "查看 Prompt 版本原文", logArgs = true, logResponse = false)
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/raw")
    public Result<PromptTemplateVersionVO> getPromptVersionRaw(@PathVariable Long versionId,
                                                               @Valid @RequestBody AiLogRawAccessDTO dto) {
        permissionGuard.require(PERM_PROMPT_RAW_VIEW);
        String lockKey = requireRawAccess("prompt-template-version-raw:" + versionId, dto);
        try {
            return Result.success(promptTemplateService.getVersionRaw(versionId));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @GetMapping("/admin/ai/prompt-template-versions/{versionId}/raw")
    public Result<PromptTemplateVersionVO> getPromptVersionRawCompat(@PathVariable Long versionId) {
        permissionGuard.require(PERM_PROMPT_RAW_VIEW);
        throw new BusinessException(ErrorCode.PARAM_ERROR,
                "Prompt 版本原文需要使用 POST 请求，并填写访问原因。");
    }

    @OperationLog(module = "ai", action = "CREATE_PROMPT_VERSION", description = "新增 Prompt 版本", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/prompt-templates/{id}/versions")
    public Result<PromptTemplateVersionVO> createPromptVersion(@PathVariable Long id,
                                                               @Valid @RequestBody PromptTemplateVersionCreateDTO dto) {
        permissionGuard.require(PERM_PROMPT_WRITE);
        return runConfirmedOperation("prompt-template-version-create:" + id + ":" + dto.getVersionCode(),
                dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                () -> promptTemplateService.createVersion(id, dto));
    }

    @OperationLog(module = "ai", action = "ACTIVATE_PROMPT_VERSION", description = "激活 Prompt 版本", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/activate")
    @Operation(summary = "激活 Prompt 版本", description = "管理端接口：激活指定 Prompt 版本，并同步模板当前内容。")
    public Result<PromptTemplateVersionVO> activatePromptVersion(@PathVariable Long versionId,
                                                                 @RequestBody(required = false)
                                                                 PromptVersionActionDTO dto) {
        permissionGuard.require(PERM_PROMPT_PUBLISH);
        PromptVersionActionDTO action = dto == null ? new PromptVersionActionDTO() : dto;
        return runConfirmedOperation("prompt-template-version-activate:" + versionId,
                action.getConfirm(), action.getDryRun(), action.getReason(), action.getIdempotencyKey(),
                () -> promptTemplateService.activateVersion(versionId, action));
    }

    @OperationLog(module = "ai", action = "ROLLBACK_PROMPT_VERSION", description = "回滚 Prompt 版本", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/rollback")
    @Operation(summary = "回滚 Prompt 版本", description = "管理端接口：等价于激活历史版本，内部复用激活逻辑并保留 changeLog。")
    public Result<PromptTemplateVersionVO> rollbackPromptVersion(@PathVariable Long versionId,
                                                                 @RequestBody(required = false)
                                                                 PromptVersionActionDTO dto) {
        permissionGuard.require(PERM_PROMPT_PUBLISH);
        PromptVersionActionDTO action = dto == null ? new PromptVersionActionDTO() : dto;
        return runConfirmedOperation("prompt-template-version-rollback:" + versionId,
                action.getConfirm(), action.getDryRun(), action.getReason(), action.getIdempotencyKey(),
                () -> promptTemplateService.rollbackVersion(versionId, action));
    }

    @OperationLog(module = "ai", action = "DISABLE_PROMPT_VERSION", description = "禁用 Prompt 版本", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/disable")
    @Operation(summary = "禁用 Prompt 版本", description = "管理端接口：禁用非激活状态的 Prompt 版本。")
    public Result<Void> disablePromptVersion(@PathVariable Long versionId,
                                             @RequestBody(required = false) PromptVersionActionDTO dto) {
        permissionGuard.require(PERM_PROMPT_PUBLISH);
        PromptVersionActionDTO action = dto == null ? new PromptVersionActionDTO() : dto;
        return runConfirmedVoidOperation("prompt-template-version-disable:" + versionId,
                action.getConfirm(), action.getDryRun(), action.getReason(), action.getIdempotencyKey(),
                () -> promptTemplateService.disableVersion(versionId, action));
    }

    @OperationLog(module = "ai", action = "TEST_PROMPT_VERSION", description = "测试 Prompt 版本", logArgs = false, logResponse = false)
    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/test")
    @Operation(summary = "测试 Prompt 版本", description = "管理端接口：渲染或调用 AI 测试指定 Prompt 版本。")
    public Result<PromptVersionTestVO> testPromptVersion(@PathVariable Long versionId,
                                                         @RequestBody(required = false) PromptVersionTestDTO dto) {
        permissionGuard.require(PERM_PROMPT_TEST);
        if (dto != null && Boolean.TRUE.equals(dto.getCallAi())) {
            if (!Boolean.TRUE.equals(dto.getConfirmSensitiveAccess())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "Real prompt AI test requires confirmSensitiveAccess=true.");
            }
            return runConfirmedOperation("prompt-template-version-test:" + versionId,
                    dto.getConfirm(), dto.getDryRun(), dto.getReason(), dto.getIdempotencyKey(),
                    () -> promptTemplateService.testVersion(versionId, dto));
        }
        return Result.success(promptTemplateService.testVersion(versionId, dto));
    }

    @GetMapping("/admin/ai/prompt-templates/{templateId}/call-logs")
    @Operation(summary = "查询模板调用记录", description = "管理端接口：按 Prompt 模板查询 AI 调用日志。")
    public Result<PageResult<AiCallLogVO>> pageTemplateCallLogs(@PathVariable Long templateId,
                                                                AiCallLogQueryDTO query) {
        permissionGuard.require(PERM_LOG_LIST);
        return Result.success(promptTemplateService.pageTemplateLogs(templateId, query));
    }

    @GetMapping("/admin/ai/prompt-template-versions/{versionId}/call-logs")
    @Operation(summary = "查询版本调用记录", description = "管理端接口：按 Prompt 版本查询 AI 调用日志。")
    public Result<PageResult<AiCallLogVO>> pageVersionCallLogs(@PathVariable Long versionId,
                                                               AiCallLogQueryDTO query) {
        permissionGuard.require(PERM_LOG_LIST);
        return Result.success(promptTemplateService.pageVersionLogs(versionId, query));
    }

    @GetMapping({"/admin/ai/call-logs", "/admin/ai/logs"})
    public Result<PageResult<AiCallLogVO>> pageLogs(AiCallLogQueryDTO query,
                                                    @RequestParam(required = false) Long pageNo,
                                                    @RequestParam(required = false) Long pageSize) {
        permissionGuard.require(PERM_LOG_LIST);
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
        permissionGuard.require(PERM_LOG_LIST);
        return Result.success(promptTemplateService.pageLogs(query));
    }

    @GetMapping({"/admin/ai/call-logs/{id}", "/admin/ai/logs/{id}"})
    public Result<AiCallLogVO> getLog(@PathVariable Long id) {
        permissionGuard.require(PERM_LOG_LIST);
        return Result.success(promptTemplateService.getLog(id));
    }

    @OperationLog(module = "ai", action = "VIEW_AI_LOG_RAW", description = "查看 AI 调用日志原文", logResponse = false)
    @PostMapping({"/admin/ai/call-logs/{id}/raw", "/admin/ai/logs/{id}/raw"})
    public Result<AiCallLogVO> getLogRaw(@PathVariable Long id,
                                          @Valid @RequestBody AiLogRawAccessDTO dto) {
        permissionGuard.require(PERM_LOG_RAW_VIEW);
        String lockKey = requireRawAccess("ai-log-raw:" + id, dto);
        try {
            return Result.success(promptTemplateService.getLogRaw(id));
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    @OperationLog(module = "ai", action = "VIEW_AI_LOG_RAW_COMPAT", description = "兼容入口查看 AI 调用日志原文", logResponse = false)
    @GetMapping({"/admin/ai/call-logs/{id}/raw", "/admin/ai/logs/{id}/raw"})
    public Result<AiCallLogVO> getLogRawCompat(@PathVariable Long id) {
        permissionGuard.require(PERM_LOG_RAW_VIEW);
        throw new BusinessException(ErrorCode.PARAM_ERROR, "AI 调用日志原文需要使用 POST 申请，并填写访问原因。");
    }

    private String requireRawAccess(String operation, AiLogRawAccessDTO dto) {
        return operationConfirmationGuard.requireConfirmed(
                operation,
                dto.isConfirmSensitiveAccess(),
                dto.getDryRun(),
                dto.getAccessReason(),
                dto.getIdempotencyKey());
    }

    private <T> Result<T> runConfirmedOperation(String operation, Boolean confirm, Boolean dryRun,
                                                String reason, String idempotencyKey, Supplier<T> action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(
                operation, confirm, dryRun, reason, idempotencyKey);
        try {
            return Result.success(action.get());
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }

    private Result<Void> runConfirmedVoidOperation(String operation, Boolean confirm, Boolean dryRun,
                                                   String reason, String idempotencyKey, Runnable action) {
        String lockKey = operationConfirmationGuard.requireConfirmed(
                operation, confirm, dryRun, reason, idempotencyKey);
        try {
            action.run();
            return Result.success();
        } catch (RuntimeException ex) {
            operationConfirmationGuard.release(lockKey);
            throw ex;
        }
    }
}
