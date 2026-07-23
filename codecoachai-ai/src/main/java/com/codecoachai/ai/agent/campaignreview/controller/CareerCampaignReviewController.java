package com.codecoachai.ai.agent.campaignreview.controller;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignMemoryCandidateConfirmDTO;
import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import com.codecoachai.ai.agent.campaignreview.service.CareerCampaignReviewService;
import com.codecoachai.ai.agent.config.V7FeatureGate;
import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agent/career-campaign-reviews")
public class CareerCampaignReviewController {

    private final CareerCampaignReviewService reviewService;
    private final V7FeatureGate featureGate;

    @PostMapping("/generate")
    public Result<CareerCampaignReviewVO> generate(
            @Valid @RequestBody CareerCampaignReviewGenerateDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignReview();
        return Result.success(reviewService.generate(userId, request));
    }

    @GetMapping("/{reviewId}")
    public Result<CareerCampaignReviewVO> detail(@PathVariable Long reviewId) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignReview();
        return Result.success(reviewService.detail(userId, reviewId));
    }

    @GetMapping("/campaigns/{campaignId}")
    public Result<CareerCampaignReviewVO> detailByCampaign(@PathVariable Long campaignId) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignReview();
        return Result.success(reviewService.detailByCampaign(userId, campaignId));
    }

    @PostMapping("/memory-candidates/{candidateId}/confirm")
    public Result<CareerCampaignReviewVO> confirm(
            @PathVariable Long candidateId,
            @Valid @RequestBody CareerCampaignMemoryCandidateConfirmDTO request) {
        Long userId = SecurityAssert.requireLoginUserId();
        featureGate.requireCampaignReview();
        return Result.success(reviewService.confirmMemoryCandidate(userId, candidateId, request));
    }
}
