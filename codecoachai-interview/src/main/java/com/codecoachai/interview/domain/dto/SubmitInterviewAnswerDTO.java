package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitInterviewAnswerDTO {

    @NotBlank(message = "answerContent is required")
    @Size(max = 5000, message = "answerContent length must be less than or equal to 5000")
    private String answerContent;

    private Long questionId;
    private Integer answerDurationSeconds;
    private Boolean needFollowUp = true;
}
