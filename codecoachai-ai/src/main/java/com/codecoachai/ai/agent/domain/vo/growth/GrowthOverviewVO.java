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
}
