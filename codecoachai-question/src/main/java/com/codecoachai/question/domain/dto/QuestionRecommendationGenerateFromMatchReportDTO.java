package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class QuestionRecommendationGenerateFromMatchReportDTO {

    @NotNull(message = "matchReportId is required")
    private Long matchReportId;
    private List<Long> gapItemIds;
    private Integer questionCount;
    private String difficultyPreference;
    private String strategy;
}
