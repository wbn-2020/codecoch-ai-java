package com.codecoachai.ai.controller;

import com.codecoachai.ai.domain.dto.AnalyzeResumeJobMatchDTO;
import com.codecoachai.ai.domain.vo.AnalyzeResumeJobMatchVO;
import com.codecoachai.ai.service.AiCallLogService;
import com.codecoachai.ai.service.AiService;
import com.codecoachai.common.core.domain.Result;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inner/ai/resume-job-match")
public class InnerResumeJobMatchAiController {

    private final AiService aiService;
    private final AiCallLogService aiCallLogService;

    @PostMapping("/analyze")
    public Result<AnalyzeResumeJobMatchVO> analyze(@RequestBody AnalyzeResumeJobMatchDTO dto) {
        return Result.success(aiService.analyzeResumeJobMatch(dto));
    }

    @PostMapping("/outcome")
    public Result<Void> markOutcome(@RequestBody Map<String, Object> outcome) {
        aiCallLogService.markDeliveryOutcome(
                asLong(outcome == null ? null : outcome.get("aiCallLogId")),
                null,
                text(outcome == null ? null : outcome.get("deliveryQuality")),
                text(outcome == null ? null : outcome.get("fallbackReasonCode")),
                text(outcome == null ? null : outcome.get("schemaVersion")),
                text(outcome == null ? null : outcome.get("validationStatus")));
        return Result.success();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        String normalized = value == null ? null : String.valueOf(value).trim();
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
