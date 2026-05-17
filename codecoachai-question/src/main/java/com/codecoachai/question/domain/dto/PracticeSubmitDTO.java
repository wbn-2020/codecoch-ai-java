package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PracticeSubmitDTO {

    @NotBlank(message = "answerContent is required")
    @Size(max = 5000, message = "answerContent length must be less than 5000")
    private String answerContent;

    private Integer answerDurationSeconds;

    private String source;
}
