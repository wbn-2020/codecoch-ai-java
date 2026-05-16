package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class PracticeRecordQueryDTO {

    private Long pageNo = 1L;
    private Long pageSize = 10L;
    private Long questionId;
    private String reviewStatus;
}
