package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;

public interface CareerCampaignReviewAiService {

    CareerCampaignReviewVO generate(CareerCampaignReviewGenerateDTO request);
}
