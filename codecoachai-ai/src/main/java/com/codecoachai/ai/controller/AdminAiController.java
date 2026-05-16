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
import com.codecoachai.common.core.domain.PageResult;
import com.codecoachai.common.core.domain.Result;
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

    @PutMapping("/admin/ai/prompts/{id}")
    public Result<PromptTemplateVO> updatePrompt(@PathVariable Long id,
                                                 @Valid @RequestBody PromptTemplateSaveDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.updatePrompt(id, dto));
    }

    @DeleteMapping("/admin/ai/prompts/{id}")
    public Result<Void> deletePrompt(@PathVariable Long id) {
        SecurityAssert.requireAdmin();
        promptTemplateService.deletePrompt(id);
        return Result.success();
    }

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

    @PostMapping("/admin/ai/prompt-templates/{id}/versions")
    public Result<PromptTemplateVersionVO> createPromptVersion(@PathVariable Long id,
                                                               @Valid @RequestBody PromptTemplateVersionCreateDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.createVersion(id, dto));
    }

    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/activate")
    public Result<PromptTemplateVersionVO> activatePromptVersion(@PathVariable Long versionId,
                                                                 @RequestBody(required = false)
                                                                 PromptVersionActionDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.activateVersion(versionId, dto));
    }

    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/disable")
    public Result<Void> disablePromptVersion(@PathVariable Long versionId,
                                             @RequestBody(required = false) PromptVersionActionDTO dto) {
        SecurityAssert.requireAdmin();
        promptTemplateService.disableVersion(versionId, dto);
        return Result.success();
    }

    @PostMapping("/admin/ai/prompt-template-versions/{versionId}/test")
    public Result<PromptVersionTestVO> testPromptVersion(@PathVariable Long versionId,
                                                         @RequestBody(required = false) PromptVersionTestDTO dto) {
        SecurityAssert.requireAdmin();
        return Result.success(promptTemplateService.testVersion(versionId, dto));
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
