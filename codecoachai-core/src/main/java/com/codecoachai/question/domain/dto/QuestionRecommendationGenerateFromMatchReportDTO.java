package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class QuestionRecommendationGenerateFromMatchReportDTO {

    @NotNull(message = "请选择匹配报告")
    private Long matchReportId;
    private List<Long> gapItemIds;
    private Integer questionCount;
    private String difficultyPreference;
    private String strategy;
}
