package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionQueryDTO {

    private Long categoryId;
    private Long tagId;
    private String difficulty;
    private String questionType;
    private String experienceLevel;
    private Integer isHighFrequency;
    private Integer status;
    private String keyword;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
