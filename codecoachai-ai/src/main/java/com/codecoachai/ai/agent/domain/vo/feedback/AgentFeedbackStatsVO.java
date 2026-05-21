package com.codecoachai.ai.agent.domain.vo.feedback;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentFeedbackStatsVO {
    private Long totalFeedbackCount;
    private Long adoptedCount;
    private Long ignoredCount;
    private Long likedCount;
    private Long dislikedCount;
    private Double adoptionRate;
    private List<FeedbackTypeCount> typeDistribution = new ArrayList<>();

    @Data
    public static class FeedbackTypeCount {
        private String feedbackType;
        private Long count;
    }
}
