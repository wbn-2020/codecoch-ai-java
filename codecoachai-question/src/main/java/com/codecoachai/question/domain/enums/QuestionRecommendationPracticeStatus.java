package com.codecoachai.question.domain.enums;

import lombok.Getter;

@Getter
public enum QuestionRecommendationPracticeStatus {

    UNPRACTICED("UNPRACTICED", "Unpracticed"),
    NOT_PRACTICABLE("NOT_PRACTICABLE", "Not practicable until matched to an official question"),
    PRACTICING("PRACTICING", "Practicing"),
    COMPLETED("COMPLETED", "Completed"),
    SKIPPED("SKIPPED", "Skipped");

    private final String code;
    private final String description;

    QuestionRecommendationPracticeStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
