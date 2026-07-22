package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PlanPreviewRequest;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PulseView;
import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangePreviewVO;
import com.codecoachai.ai.agent.service.AgentPlanSourceAdapter;
import com.codecoachai.ai.agent.service.AgentReviewPlanService;
import com.codecoachai.ai.agent.service.support.AgentBusinessTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignPulsePlanPreviewAdapter {

    private final AgentPlanSourceAdapter sourceAdapter;
    private final AgentReviewPlanService reviewPlanService;
    private final AgentBusinessTimeProvider timeProvider;

    public AgentPlanChangePreviewVO preview(
            Long userId, PulseView pulse, PlanPreviewRequest request) {
        AgentExternalPlanChangePreviewDTO external = sourceAdapter.fromCampaignPulse(
                pulse,
                request.getSelectedSemanticKeys(),
                request.getIdempotencyKey(),
                request.getMaxTotalMinutes(),
                timeProvider.today());
        return reviewPlanService.previewExternal(userId, external);
    }
}
