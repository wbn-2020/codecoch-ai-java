package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMasteryDTO {

    @NotBlank(message = "请选择掌握状态")
    private String masteryStatus;
}
