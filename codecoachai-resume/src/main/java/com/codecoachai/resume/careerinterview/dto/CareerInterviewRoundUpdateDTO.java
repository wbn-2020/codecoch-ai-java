package com.codecoachai.resume.careerinterview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CareerInterviewRoundUpdateDTO {
    @NotBlank
    private String title;
    private String resultSummary;
    private String nextStep;
    @NotNull
    private Integer expectedLockVersion;
    @NotBlank
    private String idempotencyKey;
}
