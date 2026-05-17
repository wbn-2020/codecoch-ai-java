package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class PracticeReviewDTO {

    private Long userId;
    private Long recordId;
    private Long questionId;
    private String questionTitle;
    private String questionContent;
    private String questionType;
    private String difficulty;
    private String technologyStack;
    private String knowledgePoint;
    private String referenceAnswer;
    private String analysis;
    private String answerContent;
    private Integer answerDurationSeconds;
    private String targetPosition;
    private String experienceLevel;
}
