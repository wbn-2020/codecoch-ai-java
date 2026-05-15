package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionReviewQueryDTO {

    private String reviewStatus;
    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private String difficulty;
    private String keyword;
    private String batchId;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
