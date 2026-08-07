package com.codecoachai.resume.careerinterview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerInterviewRescheduleDTO {
    @NotNull
    private LocalDateTime scheduledStartsAt;
    @NotNull
    private LocalDateTime scheduledEndsAt;
    @NotBlank
    private String timezone;
    private Long calendarEventId;
    @NotNull
    private Integer expectedLockVersion;
    @NotBlank
    private String idempotencyKey;
    private String reason;
}
