package com.codecoachai.resume.service.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExperimentQualityGatePolicy {

    public QualityDecision evaluate(long applicationCount,
                                    int completedInterviewCount,
                                    Map<?, Integer> evidenceVersionUsageCounts) {
        Map<?, Integer> versionCounts = evidenceVersionUsageCounts == null
                ? Map.of() : evidenceVersionUsageCounts;
        boolean versionComparisonAllowed = versionCounts.size() >= 2
                && versionCounts.values().stream()
                .allMatch(count -> count != null && count >= 3);
        return evaluate(applicationCount, completedInterviewCount,
                versionComparisonAllowed, versionCounts);
    }

    public QualityDecision evaluate(int sampleCount, int interviewCount, int sameVersionUsageCount) {
        return evaluate(sampleCount, interviewCount, sameVersionUsageCount >= 3,
                Map.of("minimumVersionUsageCount", sameVersionUsageCount));
    }

    public boolean allowsStrongConclusion(int sampleCount, int interviewCount, int sameVersionUsageCount) {
        QualityDecision decision = evaluate(sampleCount, interviewCount, sameVersionUsageCount);
        return decision.strongConclusionAllowed() && decision.versionComparisonAllowed();
    }

    private QualityDecision evaluate(long applicationCount,
                                     int completedInterviewCount,
                                     boolean versionComparisonAllowed,
                                     Map<?, Integer> evidenceVersionUsageCounts) {
        SampleState state = SampleState.resolve(applicationCount, completedInterviewCount);
        List<String> unsupportedConclusions = new ArrayList<>();
        List<String> weakObservations = new ArrayList<>();
        switch (state) {
            case BLOCKED_FACT_ONLY ->
                    unsupportedConclusions.add("不能判断策略有效性或渠道质量。");
            case WARN_WEAK_OBSERVATION -> {
                unsupportedConclusions.add("不能输出强策略结论或比较方向优劣。");
                weakObservations.add("当前样本仅支持弱观察，不输出强策略结论或排名。");
            }
            case WARN_INTERVIEW_BOUNDARY ->
                    weakObservations.add("可以观察投递和反馈过程趋势，但不能判断面试能力变化。");
            case PASS_REVIEWABLE ->
                    weakObservations.add("样本达到复盘口径，仍需保留岗位、渠道和时间窗口边界。");
        }
        if (completedInterviewCount < 3) {
            addUnique(unsupportedConclusions, "不能判断面试能力变化或面试表现趋势。");
        }
        if (!versionComparisonAllowed) {
            addUnique(unsupportedConclusions,
                    "每个证据或简历版本使用少于 3 次时，不比较简历或证据版本优劣。");
        }
        addUnique(unsupportedConclusions,
                "不能把结果归因到单一因素，需结合岗位、渠道、证据和时间窗口。");

        Map<String, Object> sampleBoundary = new LinkedHashMap<>();
        sampleBoundary.put("applicationCount", applicationCount);
        sampleBoundary.put("completedInterviewCount", completedInterviewCount);
        sampleBoundary.put("minWeakObservationApplications", 5);
        sampleBoundary.put("minReviewableApplications", 15);
        sampleBoundary.put("minInterviewTrendSamples", 3);
        sampleBoundary.put("minEvidenceVersionUsage", 3);
        sampleBoundary.put("evidenceVersionUsageCounts", evidenceVersionUsageCounts);
        sampleBoundary.put("versionComparisonAllowed", versionComparisonAllowed);
        sampleBoundary.put("singleFactorCausalInferenceAllowed", false);

        return new QualityDecision(
                state,
                state.gateStatus,
                state.confidenceLevel,
                state.observationLevel,
                state == SampleState.PASS_REVIEWABLE,
                versionComparisonAllowed,
                false,
                unsupportedConclusions,
                weakObservations,
                sampleBoundary);
    }

    private static void addUnique(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    public enum SampleState {
        BLOCKED_FACT_ONLY("BLOCKED", "LOW", "FACT_ONLY"),
        WARN_WEAK_OBSERVATION("WARN", "LOW", "WEAK_OBSERVATION"),
        WARN_INTERVIEW_BOUNDARY("WARN", "MEDIUM", "TREND_WITH_INTERVIEW_BOUNDARY"),
        PASS_REVIEWABLE("PASS", "HIGH", "REVIEWABLE_WITH_BOUNDARY");

        private final String gateStatus;
        private final String confidenceLevel;
        private final String observationLevel;

        SampleState(String gateStatus, String confidenceLevel, String observationLevel) {
            this.gateStatus = gateStatus;
            this.confidenceLevel = confidenceLevel;
            this.observationLevel = observationLevel;
        }

        private static SampleState resolve(long applicationCount, int completedInterviewCount) {
            if (applicationCount < 5) {
                return BLOCKED_FACT_ONLY;
            }
            if (applicationCount < 15) {
                return WARN_WEAK_OBSERVATION;
            }
            if (completedInterviewCount < 3) {
                return WARN_INTERVIEW_BOUNDARY;
            }
            return PASS_REVIEWABLE;
        }
    }

    public record QualityDecision(
            SampleState state,
            String gateStatus,
            String confidenceLevel,
            String observationLevel,
            boolean strongConclusionAllowed,
            boolean versionComparisonAllowed,
            boolean singleFactorCausalInferenceAllowed,
            List<String> unsupportedConclusions,
            List<String> weakObservations,
            Map<String, Object> sampleBoundary) {

        public QualityDecision {
            unsupportedConclusions = List.copyOf(unsupportedConclusions);
            weakObservations = List.copyOf(weakObservations);
            sampleBoundary = Map.copyOf(sampleBoundary);
        }

        public List<String> limits() {
            return unsupportedConclusions;
        }
    }
}
