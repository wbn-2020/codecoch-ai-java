package com.codecoachai.ai.agent.campaignpulse;

import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.GenerateRequest;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.HistoryView;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PlanPreviewRequest;
import com.codecoachai.ai.agent.campaignpulse.CampaignPulseModels.PulseView;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangePreviewVO;

public interface CampaignPulseService {

    PulseView generate(Long userId, GenerateRequest request);

    PulseView current(Long userId, Long campaignId);

    HistoryView history(Long userId, Long campaignId);

    AgentPlanChangePreviewVO previewPlan(Long userId, Long snapshotId, PlanPreviewRequest request);

    PulseView snapshot(Long userId, Long snapshotId);
}
