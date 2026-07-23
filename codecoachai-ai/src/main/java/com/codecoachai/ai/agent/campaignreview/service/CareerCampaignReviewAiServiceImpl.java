package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.CareerCampaignReviewAiScene;
import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerCampaignReviewAiServiceImpl implements CareerCampaignReviewAiService {

    private final CareerCampaignReviewRuleEngine ruleEngine;
    private final CareerCampaignReviewNarrativeEnhancer narrativeEnhancer;

    @Override
    public CareerCampaignReviewVO generate(CareerCampaignReviewGenerateDTO request) {
        CareerCampaignReviewVO ruleResult = ruleEngine.aggregate(request);
        try {
            CareerCampaignReviewVO enhanced = narrativeEnhancer.enhance(request, ruleResult);
            return enhanced == null ? fallback(ruleResult, "AI 结果为空，已使用规则复盘。") : enhanced;
        } catch (RuntimeException ex) {
            log.warn("Career campaign review AI scene failed, using rule fallback scene={}",
                    CareerCampaignReviewAiScene.NAME, ex);
            return fallback(ruleResult, "AI 结果不可用，已使用规则复盘。");
        }
    }

    private CareerCampaignReviewVO fallback(CareerCampaignReviewVO ruleResult, String reason) {
        ruleResult.setFallback(true);
        ruleResult.setReportStatus("FALLBACK");
        ruleResult.setFallbackReason(reason);
        ruleResult.setConfidenceLevel("LOW");
        return ruleResult;
    }
}
