package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReview;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSnapshot;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewSource;
import com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import java.util.List;

public interface CareerCampaignReviewPersistenceService {

    CareerCampaignReview findOwned(Long userId, Long reviewId);

    CareerCampaignReview findOwnedByCampaign(Long userId, Long campaignId);

    CareerCampaignReviewSnapshot currentSnapshot(Long userId, CareerCampaignReview review);

    Replay findIdempotentReplay(Long userId, Long campaignId,
                                String idempotencyKeyHash, String payloadHash);

    GenerationClaim claimGeneration(Long userId,
                                    Long campaignId,
                                    String generationFingerprint,
                                    String idempotencyKeyHash,
                                    String payloadHash);

    CareerCampaignReviewSnapshot saveClaimed(Long userId,
                                             CareerCampaignReview review,
                                             String claimToken,
                                             String idempotencyKeyHash,
                                             String payloadHash,
                                             CareerCampaignReviewVO result,
                                             String inputHash,
                                             String requestId,
                                             List<CareerCampaignReviewSource> sources);

    void releaseClaim(Long userId, Long reviewId, String claimToken);

    CareerCampaignReviewMemoryCandidate confirmCandidate(Long userId,
                                                         Long candidateId,
                                                         String idempotencyKeyHash,
                                                         boolean confirmed);

    record GenerationClaim(CareerCampaignReview review,
                           CareerCampaignReviewSnapshot replay,
                           String claimToken,
                           Integer nextSnapshotVersion,
                           boolean owner) {
    }

    record Replay(CareerCampaignReview review, CareerCampaignReviewSnapshot snapshot) {
    }
}
