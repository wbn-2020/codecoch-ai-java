package com.codecoachai.resume.careerinterview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CareerInterviewProcessCreateDTO {
    @NotBlank
    private String idempotencyKey;
}
