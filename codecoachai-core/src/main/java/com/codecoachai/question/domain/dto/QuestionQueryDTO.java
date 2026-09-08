package com.codecoachai.question.domain.dto;

import lombok.Data;

@Data
public class QuestionQueryDTO {

    private Long categoryId;
    private Long tagId;
    private Long questionId;
    private String difficulty;
    private String questionType;
    private String experienceLevel;
    private Integer isHighFrequency;
    private Integer status;
    private String keyword;
    /** 错题列表专用：仅返回已到间隔复习期的错题 */
    private Boolean dueOnly;
    private Long pageNo = 1L;
    private Long pageSize = 10L;
}
