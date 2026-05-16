package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionDuplicateReviewQueryDTO {

    private Long questionId;
    private String reviewStatus;
    private String matchType;
    private String keyword;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
