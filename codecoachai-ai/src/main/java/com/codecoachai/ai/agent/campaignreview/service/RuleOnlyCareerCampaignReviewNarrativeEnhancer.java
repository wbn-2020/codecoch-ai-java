package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import org.springframework.stereotype.Component;

@Component
public class RuleOnlyCareerCampaignReviewNarrativeEnhancer implements CareerCampaignReviewNarrativeEnhancer {

    @Override
    public CareerCampaignReviewVO enhance(
            CareerCampaignReviewGenerateDTO request,
            CareerCampaignReviewVO ruleResult) {
        return ruleResult;
    }
}
