package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class PracticeReviewDTO {

    private Long userId;
    private Long recordId;
    private Long questionId;
    private String questionTitle;
    private String questionContent;
    private String referenceAnswer;
    private String answerContent;
}
