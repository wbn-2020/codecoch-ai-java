package com.codecoachai.question.domain.vo;

import lombok.Data;

@Data
public class QuestionSummaryVO {

    private Long id;
    private String title;
    private String content;
    private Long categoryId;
    private Long groupId;
    private String groupName;
    private String difficulty;
    private String questionType;
    private Integer status;
}
