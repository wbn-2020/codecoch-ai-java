package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class QuestionRecommendationGenerateFromGapDTO {

    @NotNull(message = "请选择能力画像")
    private Long skillProfileId;
    private List<Long> gapItemIds;
    private Integer questionCount;
    private String difficultyPreference;
    private String strategy;
}
