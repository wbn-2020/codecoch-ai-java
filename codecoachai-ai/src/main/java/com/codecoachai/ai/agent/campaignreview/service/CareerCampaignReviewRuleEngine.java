package com.codecoachai.ai.agent.campaignreview.service;

import com.codecoachai.ai.agent.campaignreview.domain.dto.CareerCampaignReviewGenerateDTO;
import com.codecoachai.ai.agent.campaignreview.domain.vo.CareerCampaignReviewVO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CareerCampaignReviewRuleEngine {

    public CareerCampaignReviewVO aggregate(CareerCampaignReviewGenerateDTO request) {
        requireCompleted(request);
        CareerCampaignReviewVO result = new CareerCampaignReviewVO();
        result.setReportStatus("RULE_READY");
        result.setDataCutoffAt(request.getDataCutoffAt());
        result.setFallback(false);
        result.setConfidenceLevel(sampleSize(request) < 3 ? "LOW" : "MEDIUM");
        result.setFacts(facts(request.getFacts()));
        result.setCoverage(coverage(result.getFacts()));
        result.setLimits(limits(request));
        result.setSignals(signals(request, result.getConfidenceLevel()));
        result.setMemoryCandidates(seeds(request.getMemoryCandidateSeeds(), result.getConfidenceLevel()));
        result.setExperimentCandidates(seeds(request.getExperimentCandidateSeeds(), result.getConfidenceLevel()));
        result.setNextCycleActions(seeds(request.getNextCycleActionSeeds(), result.getConfidenceLevel()));
        result.setSummary(summary(result));
        if ("LOW".equals(result.getConfidenceLevel())) {
            result.getLimits().add("样本量不足，不输出因果结论。");
        }
        return result;
    }

    private void requireCompleted(CareerCampaignReviewGenerateDTO request) {
        if (request == null || !Boolean.TRUE.equals(request.getCompleted())
                || !Boolean.TRUE.equals(request.getAllOpportunitiesClosed())) {
            throw new IllegalArgumentException("只有已完成且机会已关闭的周期可以生成最终复盘");
        }
        if (request.getDataCutoffAt() == null) {
            throw new IllegalArgumentException("dataCutoffAt is required");
        }
    }

    private List<CareerCampaignReviewVO.Fact> facts(List<CareerCampaignReviewGenerateDTO.Fact> source) {
        List<CareerCampaignReviewVO.Fact> result = new ArrayList<>();
        for (CareerCampaignReviewGenerateDTO.Fact item : safe(source)) {
            if (item == null || !hasText(item.getKey())) {
                continue;
            }
            CareerCampaignReviewVO.Fact fact = new CareerCampaignReviewVO.Fact();
            fact.setKey(item.getKey().trim());
            fact.setLabel(item.getLabel());
            fact.setValue(item.getValue());
            fact.setSourceRef(item.getSourceRef());
            result.add(fact);
        }
        return result;
    }

    private List<String> coverage(List<CareerCampaignReviewVO.Fact> facts) {
        Set<String> result = new LinkedHashSet<>();
        for (CareerCampaignReviewVO.Fact fact : facts) {
            if (hasText(fact.getSourceRef())) {
                result.add(fact.getSourceRef());
            }
        }
        return new ArrayList<>(result);
    }

    private List<String> limits(CareerCampaignReviewGenerateDTO request) {
        List<String> result = new ArrayList<>();
        if (sampleSize(request) < 3) {
            result.add("LOW_SAMPLE");
        }
        if (request.getFacts() == null || request.getFacts().isEmpty()) {
            result.add("NO_FACTS");
        }
        return result;
    }

    private List<CareerCampaignReviewVO.Signal> signals(
            CareerCampaignReviewGenerateDTO request, String confidenceLevel) {
        List<CareerCampaignReviewVO.Signal> result = new ArrayList<>();
        for (CareerCampaignReviewGenerateDTO.Seed seed : safe(request.getExperimentCandidateSeeds())) {
            if (seed == null || !hasText(seed.getSemanticKey())) {
                continue;
            }
            CareerCampaignReviewVO.Signal signal = new CareerCampaignReviewVO.Signal();
            signal.setKey(seed.getSemanticKey());
            signal.setTitle(seed.getTitle());
            signal.setDescription(seed.getDescription());
            signal.setConfidenceLevel(confidenceLevel);
            if ("LOW".equals(confidenceLevel) || Boolean.TRUE.equals(seed.getCausalClaim())) {
                signal.setBlockedConclusions(List.of("不能据此认定因果关系"));
            }
            result.add(signal);
        }
        return result;
    }

    private List<CareerCampaignReviewVO.Seed> seeds(
            List<CareerCampaignReviewGenerateDTO.Seed> source, String confidenceLevel) {
        List<CareerCampaignReviewVO.Seed> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (CareerCampaignReviewGenerateDTO.Seed item : safe(source)) {
            if (item == null || !hasText(item.getSemanticKey()) || !keys.add(item.getSemanticKey())) {
                continue;
            }
            CareerCampaignReviewVO.Seed seed = new CareerCampaignReviewVO.Seed();
            seed.setSemanticKey(item.getSemanticKey());
            seed.setTitle(item.getTitle());
            seed.setDescription(item.getDescription());
            seed.setSourceRef(item.getSourceRef());
            seed.setConfidenceLevel("LOW".equals(confidenceLevel) ? "LOW" : normalizeConfidence(item.getConfidenceLevel()));
            seed.setValidityDays(item.getValidityDays());
            result.add(seed);
        }
        return result;
    }

    private String summary(CareerCampaignReviewVO result) {
        return "基于截止时间 " + result.getDataCutoffAt()
                + " 聚合 " + result.getFacts().size() + " 项事实，"
                + ("LOW".equals(result.getConfidenceLevel()) ? "样本有限，已限制结论范围。" : "可用于流程信号和下一周期动作排序。");
    }

    private int sampleSize(CareerCampaignReviewGenerateDTO request) {
        return request.getSampleSize() == null ? 0 : Math.max(request.getSampleSize(), 0);
    }

    private String normalizeConfidence(String value) {
        String normalized = value == null ? "MEDIUM" : value.trim().toUpperCase(Locale.ROOT);
        return Set.of("LOW", "MEDIUM", "HIGH").contains(normalized) ? normalized : "MEDIUM";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
