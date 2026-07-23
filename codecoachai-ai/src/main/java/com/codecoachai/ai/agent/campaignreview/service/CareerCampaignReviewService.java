package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignMemoryCandidateConfirmDTO;
import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;

public interface CareerCampaignReviewService {

    CareerCampaignReviewVO generate(Long userId, CareerCampaignReviewGenerateDTO request);

    CareerCampaignReviewVO detail(Long userId, Long reviewId);

    CareerCampaignReviewVO detailByCampaign(Long userId, Long campaignId);

    CareerCampaignReviewVO confirmMemoryCandidate(
            Long userId, Long candidateId, CareerCampaignMemoryCandidateConfirmDTO request);
}
