package com.codecoachai.interview.feign.dto;

import lombok.Data;

@Data
public class GenerateInterviewQuestionDTO {

    private String mode;
    private String stageType;
    private Long questionId;
    private String questionTitle;
    private String questionContent;
    private String resumeSummary;
}
