package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class QuestionRecommendationGenerateFromStudyPlanDTO {

    @NotNull(message = "请选择学习计划")
    private Long studyPlanId;
    private List<Long> gapItemIds;
    private Integer questionCount;
    private String difficultyPreference;
    private String strategy;
}
