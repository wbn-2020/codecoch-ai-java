package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class StudyPlanGenerateFromMatchReportDTO {

    @NotNull(message = "请选择匹配报告")
    private Long matchReportId;

    @Min(value = 1, message = "学习天数需在 1 到 60 天之间")
    @Max(value = 60, message = "学习天数需在 1 到 60 天之间")
    private Integer days;

    @Min(value = 15, message = "每天学习时长需在 15 到 480 分钟之间")
    @Max(value = 480, message = "每天学习时长需在 15 到 480 分钟之间")
    private Integer dailyMinutes;

    private LocalDate startDate;

    private String planTitle;
}
