package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PracticeSubmitDTO {

    @NotBlank(message = "请填写练习回答")
    @Size(max = 5000, message = "练习回答不能超过 5000 字")
    private String answerContent;

    private Integer answerDurationSeconds;

    private String source;

    private Long recommendationItemId;

    private Long batchId;

    private String sourceType;

    private Long sourceId;

    private Long skillProfileId;

    private Long studyPlanId;

    private Long questionId;
}
