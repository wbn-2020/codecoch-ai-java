package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionDuplicateEvalCaseQueryDTO {

    private String keyword;
    private String expected;
    private Integer enabled;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
