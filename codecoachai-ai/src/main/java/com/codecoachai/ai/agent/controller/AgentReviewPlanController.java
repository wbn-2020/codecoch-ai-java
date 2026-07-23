package com.codecoachai.ai.agent.controller;

import com.codecoachai.ai.agent.config.V4FeatureGate;
import com.codecoachai.ai.agent.config.V7FeatureGate;
import com.codecoachai.ai.agent.domain.dto.AgentPlanChangeConfirmDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewPlanDecisionDTO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeConfirmVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangePreviewVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewPlanSuggestionListVO;
import com.codecoachai.ai.agent.service.AgentReviewPlanService;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent")
public class AgentReviewPlanController {

    private final AgentReviewPlanService reviewPlanService;
    private final V4FeatureGate featureGate;
    private final V7FeatureGate v7FeatureGate;

    @GetMapping("/reviews/{reviewId}/plan-suggestions")
    public Result<AgentReviewPlanSuggestionListVO> suggestions(@PathVariable Long reviewId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(reviewPlanService.suggestions(userId, reviewId));
    }

    @PostMapping("/reviews/{reviewId}/plan-suggestions/decisions")
    public Result<AgentReviewPlanSuggestionListVO> decide(
            @PathVariable Long reviewId,
            @Valid @RequestBody AgentReviewPlanDecisionDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireAdaptivePlanEnabled();
        return Result.success(reviewPlanService.decide(userId, reviewId, dto));
    }

    @PostMapping("/reviews/{reviewId}/plan-change-previews")
    public Result<AgentPlanChangePreviewVO> preview(
            @PathVariable Long reviewId,
            @Valid @RequestBody AgentPlanChangePreviewDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireAdaptivePlanEnabled();
        return Result.success(reviewPlanService.preview(userId, reviewId, dto));
    }

    @PostMapping("/plan-changes/external/preview")
    public Result<AgentPlanChangePreviewVO> previewExternal(
            @Valid @RequestBody AgentExternalPlanChangePreviewDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireAdaptivePlanEnabled();
        v7FeatureGate.requireExternalPlanSource();
        return Result.success(reviewPlanService.previewExternal(userId, dto));
    }

    @GetMapping("/plan-change-sets/{changeSetId}")
    public Result<AgentPlanChangePreviewVO> changeSet(@PathVariable Long changeSetId) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(reviewPlanService.changeSet(userId, changeSetId));
    }

    @PostMapping("/plan-change-sets/{changeSetId}/confirm")
    public Result<AgentPlanChangeConfirmVO> confirm(
            @PathVariable Long changeSetId,
            @Valid @RequestBody AgentPlanChangeConfirmDTO dto) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireAdaptivePlanEnabled();
        return Result.success(reviewPlanService.confirm(userId, changeSetId, dto));
    }

    @GetMapping("/plan-change-sets")
    public Result<List<AgentPlanChangePreviewVO>> changeSets(
            @RequestParam(required = false) LocalDate targetDate,
            @RequestParam(required = false) String status) {
        Long userId = SecurityAssert.requireLoginUserId();
        return Result.success(reviewPlanService.changeSets(userId, targetDate, status));
    }
}
