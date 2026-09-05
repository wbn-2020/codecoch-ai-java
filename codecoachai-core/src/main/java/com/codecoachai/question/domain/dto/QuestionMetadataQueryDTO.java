package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionMetadataQueryDTO {

    private String keyword;
    private Long categoryId;
    private Integer status;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
