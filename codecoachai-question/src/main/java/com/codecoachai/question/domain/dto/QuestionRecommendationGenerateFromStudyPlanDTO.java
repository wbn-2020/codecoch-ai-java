package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class QuestionRecommendationGenerateFromStudyPlanDTO {

    @NotNull(message = "studyPlanId is required")
    private Long studyPlanId;
    private List<Long> gapItemIds;
    private Integer questionCount;
    private String difficultyPreference;
    private String strategy;
}
