package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateMasteryDTO {

    @NotBlank(message = "masteryStatus is required")
    private String masteryStatus;
}
