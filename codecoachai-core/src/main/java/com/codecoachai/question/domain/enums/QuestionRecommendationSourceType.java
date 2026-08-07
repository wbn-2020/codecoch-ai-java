package com.codecoachai.question.domain.enums;

import lombok.Getter;

@Getter
public enum QuestionRecommendationSourceType {

    JD_GAP("JD_GAP", "Skill profile gap"),
    RESUME_JOB_MATCH("RESUME_JOB_MATCH", "Resume job match report"),
    STUDY_PLAN("STUDY_PLAN", "Gap-driven study plan");

    private final String code;
    private final String description;

    QuestionRecommendationSourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
