package com.codecoachai.resume.careerreview;

import java.time.LocalDateTime;

public interface CareerCampaignReviewEvidenceService {

    default CareerCampaignReviewEvidenceVO get(
            Long userId, Long campaignId, LocalDateTime dataCutoffAt) {
        return get(userId, campaignId, dataCutoffAt, null, null);
    }

    CareerCampaignReviewEvidenceVO get(Long userId,
                                       Long campaignId,
                                       LocalDateTime dataCutoffAt,
                                       Integer applicationLimit,
                                       Integer eventLimitPerSection);
}
