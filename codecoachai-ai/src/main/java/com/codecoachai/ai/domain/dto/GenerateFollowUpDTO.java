package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class GenerateFollowUpDTO {

    private Long questionId;
    private String questionTitle;
    private String answerContent;
    private String comment;
}
