package com.codecoachai.question.domain.vo;

import lombok.Data;

@Data
public class InnerQuestionVO {

    private Long id;
    private Long groupId;
    private String title;
    private String content;
    private String referenceAnswer;
    private String analysis;
    private String difficulty;
}
