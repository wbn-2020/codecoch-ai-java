package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionDuplicateReviewQueryDTO {

    private Long questionId;
    /**
     * Public status alias. Supports ALL/全部, PENDING, MERGED, CONFIRMED and IGNORED.
     */
    private String status;
    private String reviewStatus;
    private String matchType;
    private String scoreBand;
    private String keyword;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
