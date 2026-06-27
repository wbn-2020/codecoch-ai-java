package com.codecoachai.ai.agent.domain.vo.growth;

import com.codecoachai.ai.agent.domain.vo.analytics.MetricPointVO;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GrowthOverviewVO {
    private Integer readinessScore;
    private Double taskCompletionRate;
    private Double agentSuccessRate;
    private Long totalReviewCount;
    private Long totalMemoryCount;
    private List<MetricPointVO> topSkills = new ArrayList<>();
    private String confidenceLevel;
    private Integer evidenceCount;
    private String timeWindow;
    private List<String> dataSourceLabels = new ArrayList<>();
    private String coldStartReason;
    private List<String> nextEvidenceActions = new ArrayList<>();
    private DisplayPolicy displayPolicy = DisplayPolicy.coldStart();

    @Data
    public static class DisplayPolicy {
        private Boolean showStrongScore;
        private Boolean showReadinessTrend;
        private Boolean showTopSkillTrend;
        private Boolean showPercentileComparison;
        private Boolean showGapPercentage;

        public static DisplayPolicy coldStart() {
            DisplayPolicy policy = new DisplayPolicy();
            policy.setShowStrongScore(false);
            policy.setShowReadinessTrend(false);
            policy.setShowTopSkillTrend(false);
            policy.setShowPercentileComparison(false);
            policy.setShowGapPercentage(false);
            return policy;
        }

        public static DisplayPolicy trusted(boolean hasTrendData, boolean hasSkillData) {
            DisplayPolicy policy = coldStart();
            policy.setShowStrongScore(true);
            policy.setShowReadinessTrend(hasTrendData);
            policy.setShowTopSkillTrend(hasSkillData);
            return policy;
        }
    }
}
