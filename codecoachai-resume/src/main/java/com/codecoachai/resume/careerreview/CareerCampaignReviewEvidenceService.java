package com.codecoachai.resume.careerreview;

import java.time.LocalDateTime;

public interface CareerCampaignReviewEvidenceService {

    CareerCampaignReviewEvidenceVO get(Long userId, Long campaignId, LocalDateTime dataCutoffAt);
}
