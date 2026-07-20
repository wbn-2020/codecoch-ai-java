package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignMemoryCandidateVO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CareerCampaignReviewMemoryCandidateService {

    public List<CareerCampaignMemoryCandidateVO> candidates(CareerCampaignReviewVO review) {
        Map<String, CareerCampaignMemoryCandidateVO> unique = new LinkedHashMap<>();
        if (review == null || review.getMemoryCandidates() == null) {
            return List.of();
        }
        for (CareerCampaignReviewVO.Seed seed : review.getMemoryCandidates()) {
            if (seed == null || seed.getTitle() == null || seed.getDescription() == null) {
                continue;
            }
            CareerCampaignMemoryCandidateVO candidate = new CareerCampaignMemoryCandidateVO();
            candidate.setSemanticHash(AgentAdaptivePlanHashUtils.sha256(
                    seed.getTitle().trim() + "|" + seed.getDescription().trim()));
            candidate.setTitle(seed.getTitle());
            candidate.setContent(seed.getDescription());
            candidate.setSourceRef(seed.getSourceRef());
            candidate.setConfidenceLevel(seed.getConfidenceLevel());
            candidate.setValidityDays(seed.getValidityDays());
            if (seed.getValidityDays() != null && seed.getValidityDays() > 0) {
                candidate.setExpiresAt(LocalDateTime.now().plusDays(seed.getValidityDays()));
            }
            unique.putIfAbsent(candidate.getSemanticHash(), candidate);
        }
        return new ArrayList<>(unique.values());
    }

    public CareerCampaignMemoryCandidateVO confirm(
            CareerCampaignMemoryCandidateVO candidate, boolean confirmed) {
        if (candidate == null) {
            throw new IllegalArgumentException("memory candidate is required");
        }
        if (!confirmed) {
            candidate.setStatus("REJECTED");
            return candidate;
        }
        candidate.setStatus("CONFIRMED");
        return candidate;
    }

    public boolean effective(CareerCampaignMemoryCandidateVO candidate, LocalDateTime now) {
        return candidate != null
                && "CONFIRMED".equals(candidate.getStatus())
                && (candidate.getExpiresAt() == null
                || candidate.getExpiresAt().isAfter(now == null ? LocalDateTime.now() : now));
    }
}
