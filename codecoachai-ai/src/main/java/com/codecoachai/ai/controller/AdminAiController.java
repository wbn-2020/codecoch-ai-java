package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.PromptTemplateSaveDTO;
import com.codecoachai.ai.domain.dto.UpdatePromptStatusDTO;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.service.PromptTemplateService;
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
        return Result.success(promptTemplateService.pagePrompts(pageNo, pageSize, keyword, scene, status));
    }

    @PostMapping("/admin/ai/prompts")
    public Result<PromptTemplateVO> createPrompt(@Valid @RequestBody PromptTemplateSaveDTO dto) {
        return Result.success(promptTemplateService.createPrompt(dto));
    }

    @PutMapping("/admin/ai/prompts/{id}")
    public Result<PromptTemplateVO> updatePrompt(@PathVariable Long id,
                                                 @Valid @RequestBody PromptTemplateSaveDTO dto) {
        return Result.success(promptTemplateService.updatePrompt(id, dto));
    }

    @DeleteMapping("/admin/ai/prompts/{id}")
    public Result<Void> deletePrompt(@PathVariable Long id) {
        promptTemplateService.deletePrompt(id);
        return Result.success();
    }

    @PutMapping("/admin/ai/prompts/{id}/status")
    public Result<Void> updatePromptStatus(@PathVariable Long id,
                                           @Valid @RequestBody UpdatePromptStatusDTO dto) {
        promptTemplateService.updateStatus(id, dto);
        return Result.success();
    }

    @GetMapping({"/admin/ai/call-logs", "/admin/ai/logs"})
    public Result<PageResult<AiCallLogVO>> pageLogs(@RequestParam(required = false) Long pageNo,
                                                    @RequestParam(required = false) Long pageSize) {
        return Result.success(promptTemplateService.pageLogs(pageNo, pageSize));
    }

    @GetMapping({"/admin/ai/call-logs/{id}", "/admin/ai/logs/{id}"})
    public Result<AiCallLogVO> getLog(@PathVariable Long id) {
        return Result.success(promptTemplateService.getLog(id));
    }
}
