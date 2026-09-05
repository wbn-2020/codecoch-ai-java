package com.codecoachai.resume.careercontact.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CareerActivityRecordDTO {
    @NotBlank
    private String idempotencyKey;
}
