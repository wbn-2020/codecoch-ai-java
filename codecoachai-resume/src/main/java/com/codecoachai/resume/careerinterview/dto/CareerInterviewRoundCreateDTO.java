package com.codecoachai.resume.careerinterview.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerInterviewRoundCreateDTO {
    @NotBlank
    private String roundType;
    @NotBlank
    private String title;
    private String timezone;
    private LocalDateTime scheduledStartsAt;
    private LocalDateTime scheduledEndsAt;
    @NotBlank
    private String idempotencyKey;
}
