package com.codecoachai.question.domain.enums;

import lombok.Getter;

@Getter
public enum QuestionRecommendationBatchStatus {

    GENERATING("GENERATING", "Generating"),
    SUCCESS("SUCCESS", "Success"),
    FAILED("FAILED", "Failed");

    private final String code;
    private final String description;

    QuestionRecommendationBatchStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
