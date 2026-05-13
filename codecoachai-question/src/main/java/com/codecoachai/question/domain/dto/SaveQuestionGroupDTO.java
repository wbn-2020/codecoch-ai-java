package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveQuestionGroupDTO {

    @NotBlank(message = "groupName is required")
    private String groupName;

    private String description;
    private Long categoryId;
    private Integer status;
}
