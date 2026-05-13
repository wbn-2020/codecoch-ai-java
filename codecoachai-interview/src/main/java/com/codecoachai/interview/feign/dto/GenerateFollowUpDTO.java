package com.codecoachai.interview.feign.dto;

import lombok.Data;

@Data
public class GenerateFollowUpDTO {

    private Long questionId;
    private String questionTitle;
    private String answerContent;
    private String comment;
}
