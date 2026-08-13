package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitQuestionAnswerDTO {

    @NotBlank(message = "请填写练习回答")
    private String answerContent;

    private String masteryStatus;

    private Long targetJobId;
}
