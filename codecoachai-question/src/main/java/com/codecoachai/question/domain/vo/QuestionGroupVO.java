package com.codecoachai.question.domain.vo;

import lombok.Data;

@Data
public class QuestionGroupVO {

    private Long id;
    private String groupName;
    private String description;
    private Long categoryId;
    private Integer status;
}
