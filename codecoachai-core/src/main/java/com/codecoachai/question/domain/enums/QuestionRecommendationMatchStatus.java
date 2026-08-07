package com.codecoachai.question.domain.enums;

import lombok.Getter;

@Getter
public enum QuestionRecommendationMatchStatus {

    MATCHED("MATCHED", "Matched to an official question"),
    UNMATCHED_DRAFT("UNMATCHED_DRAFT", "AI draft without an official question");

    private final String code;
    private final String description;

    QuestionRecommendationMatchStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
