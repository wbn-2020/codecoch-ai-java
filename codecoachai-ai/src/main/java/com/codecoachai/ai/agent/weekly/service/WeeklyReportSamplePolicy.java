package com.codecoachai.ai.agent.weekly.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class WeeklyReportSamplePolicy {

    public static final int MIN_WEAK_APPLICATIONS = 5;
    public static final int MIN_STRATEGY_APPLICATIONS = 10;
    public static final int MIN_COMPARABLE_INTERVIEWS = 3;
    public static final int MIN_RESUME_VERSION_USES = 3;
    public static final int MIN_CHANNEL_MATURED_APPLICATIONS = 5;
    public static final int MIN_COMPARABLE_WEEKS = 3;
    public static final int MIN_DAILY_TASKS = 3;
    public static final int MIN_DAILY_DONE_TASKS = 2;

    public Decision evaluate(Metrics metrics) {
        Decision decision = new Decision();
        int applications = value(metrics.getApplicationActivityCount());
        int matured = value(metrics.getMaturedApplicationCount());
        int interviews = value(metrics.getTrustedComparableInterviewCount());
        int comparableWeeks = value(metrics.getComparableWeekCount());

        decision.setWeakObservationAllowed(applications >= MIN_WEAK_APPLICATIONS);
        decision.setStrategyExperimentAllowed(
                matured >= MIN_STRATEGY_APPLICATIONS
                        && !Boolean.TRUE.equals(metrics.getAllScope())
                        && !Boolean.TRUE.equals(metrics.getCareerUnavailable()));
        decision.setInterviewTrendAllowed(
                interviews >= MIN_COMPARABLE_INTERVIEWS
                        && !Boolean.TRUE.equals(metrics.getAllScope())
                        && !Boolean.TRUE.equals(metrics.getInterviewUnavailable()));
        decision.setMultiWeekTrendAllowed(comparableWeeks >= MIN_COMPARABLE_WEEKS);
        decision.setDailyExecutionTrendAllowed(
                value(metrics.getDailyTaskCount()) >= MIN_DAILY_TASKS
                        && value(metrics.getDailyDoneCount()) >= MIN_DAILY_DONE_TASKS);

        if (applications < MIN_WEAK_APPLICATIONS || matured == 0) {
            decision.setConfidenceLevel("FACT_ONLY");
        } else if (matured < MIN_STRATEGY_APPLICATIONS
                || Boolean.TRUE.equals(metrics.getCareerUnavailable())
                || Boolean.TRUE.equals(metrics.getDailyEvidenceInsufficient())) {
            decision.setConfidenceLevel("LOW");
        } else if (interviews < MIN_COMPARABLE_INTERVIEWS
                || comparableWeeks < MIN_COMPARABLE_WEEKS
                || Boolean.TRUE.equals(metrics.getInterviewUnavailable())
                || Boolean.TRUE.equals(metrics.getTruncated())
                || Boolean.TRUE.equals(metrics.getAllScope())) {
            decision.setConfidenceLevel("MEDIUM");
        } else {
            decision.setConfidenceLevel("HIGH");
        }

        List<String> blocked = new ArrayList<>();
        if (matured < MIN_STRATEGY_APPLICATIONS) {
            blocked.add("strategyEffectiveness");
            blocked.add("channelQuality");
            blocked.add("resumeVersionComparison");
        }
        if (interviews < MIN_COMPARABLE_INTERVIEWS) {
            blocked.add("interviewAbilityTrend");
        }
        if (comparableWeeks < MIN_COMPARABLE_WEEKS) {
            blocked.add("multiWeekStableTrend");
        }
        if (Boolean.TRUE.equals(metrics.getAllScope())) {
            blocked.add("crossJobStrategyComparison");
        }
        blocked.add("singleFactorCausalConclusion");
        decision.setBlockedConclusions(blocked.stream().distinct().toList());
        return decision;
    }

    public boolean channelComparisonAllowed(Map<String, SegmentMetric> channels, boolean allScope) {
        if (allScope || channels == null || channels.size() < 2) {
            return false;
        }
        int total = channels.values().stream()
                .mapToInt(item -> value(item == null ? null : item.getMaturedCount()))
                .sum();
        return total >= MIN_STRATEGY_APPLICATIONS
                && channels.values().stream()
                .allMatch(item -> item != null
                        && value(item.getMaturedCount()) >= MIN_CHANNEL_MATURED_APPLICATIONS);
    }

    public boolean resumeVersionComparisonAllowed(Map<String, SegmentMetric> versions, boolean allScope) {
        if (allScope || versions == null || versions.size() < 2) {
            return false;
        }
        int maturedTotal = versions.values().stream()
                .mapToInt(item -> value(item == null ? null : item.getMaturedCount()))
                .sum();
        return maturedTotal >= MIN_STRATEGY_APPLICATIONS
                && versions.values().stream()
                .allMatch(item -> item != null
                        && value(item.getActivityCount()) >= MIN_RESUME_VERSION_USES
                        && value(item.getMaturedCount()) >= MIN_RESUME_VERSION_USES);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    @Data
    public static class Metrics {

        private Integer applicationActivityCount;
        private Integer maturedApplicationCount;
        private Integer trustedComparableInterviewCount;
        private Integer comparableWeekCount;
        private Integer dailyTaskCount;
        private Integer dailyDoneCount;
        private Boolean allScope;
        private Boolean careerUnavailable;
        private Boolean interviewUnavailable;
        private Boolean dailyEvidenceInsufficient;
        private Boolean truncated;
    }

    @Data
    public static class SegmentMetric {

        private Integer activityCount;
        private Integer maturedCount;
        private Integer verifiedResponseCount;
    }

    @Data
    public static class Decision {

        private String confidenceLevel;
        private Boolean weakObservationAllowed;
        private Boolean strategyExperimentAllowed;
        private Boolean interviewTrendAllowed;
        private Boolean multiWeekTrendAllowed;
        private Boolean dailyExecutionTrendAllowed;
        private List<String> blockedConclusions = new ArrayList<>();
    }
}
