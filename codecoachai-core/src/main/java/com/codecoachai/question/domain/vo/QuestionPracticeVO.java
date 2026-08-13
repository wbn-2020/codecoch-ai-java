package com.codecoachai.question.domain.vo;

import lombok.Data;

@Data
public class QuestionPracticeVO {

    private Long id;
    private String title;
    private String content;
    private Long categoryId;
    private Long groupId;
    private String difficulty;
    private String questionType;
    private String experienceLevel;
    private Integer isHighFrequency;
    private Integer status;
}
