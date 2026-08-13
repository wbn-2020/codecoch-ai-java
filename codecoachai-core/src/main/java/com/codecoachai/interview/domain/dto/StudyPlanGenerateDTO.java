package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class StudyPlanGenerateDTO {

    @NotNull(message = "请选择面试报告")
    private Long reportId;
    private Long resumeId;
    private Long optimizeRecordId;
    private String targetPosition;
    private String industryDirection;
    @Min(value = 1, message = "学习天数需要在 1 到 60 天之间")
    @Max(value = 60, message = "学习天数需要在 1 到 60 天之间")
    private Integer expectedDurationDays;
    @Min(value = 15, message = "每天学习时长需在 15 到 480 分钟之间")
    @Max(value = 480, message = "每天学习时长需在 15 到 480 分钟之间")
    private Integer dailyMinutes;
    private String extraRequirements;
}
