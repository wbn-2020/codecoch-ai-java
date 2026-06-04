package com.codecoachai.ai.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AiResultFeedbackStatsVO {

    private Long totalFeedbackCount = 0L;
    private Long inaccurateCount = 0L;
    private Long hallucinationCount = 0L;
    private Long notMyExperienceCount = 0L;
    private Long negativeFeedbackCount = 0L;
    private Double negativeFeedbackRate = 0.0;
    private List<FeedbackTypeCount> typeDistribution = new ArrayList<>();

    @Data
    public static class FeedbackTypeCount {
        private String feedbackType;
        private Long count;
    }
}
