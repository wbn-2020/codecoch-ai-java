package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class StudyPlanGenerateFromMatchReportDTO {

    @NotNull(message = "matchReportId is required")
    private Long matchReportId;

    @Min(value = 1, message = "days must be between 1 and 60")
    @Max(value = 60, message = "days must be between 1 and 60")
    private Integer days;

    @Min(value = 15, message = "dailyMinutes must be between 15 and 480")
    @Max(value = 480, message = "dailyMinutes must be between 15 and 480")
    private Integer dailyMinutes;

    private LocalDate startDate;

    private String planTitle;
}
