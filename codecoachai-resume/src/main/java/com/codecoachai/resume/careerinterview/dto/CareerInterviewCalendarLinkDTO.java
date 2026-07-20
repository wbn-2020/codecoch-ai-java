package com.codecoachai.resume.careerinterview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CareerInterviewCalendarLinkDTO {
    @NotNull
    private Long calendarEventId;
    @NotNull
    private Integer expectedLockVersion;
    @NotBlank
    private String idempotencyKey;
}
