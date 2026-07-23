package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;

@FunctionalInterface
public interface CareerCampaignReviewNarrativeEnhancer {

    CareerCampaignReviewVO enhance(
            CareerCampaignReviewGenerateDTO request,
            CareerCampaignReviewVO ruleResult);
}
