package com.codecoachai.resume.careerinterview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CareerInterviewTransitionDTO {
    @NotBlank
    private String targetStatus;
    @NotNull
    private Integer expectedLockVersion;
    @NotBlank
    private String idempotencyKey;
}
