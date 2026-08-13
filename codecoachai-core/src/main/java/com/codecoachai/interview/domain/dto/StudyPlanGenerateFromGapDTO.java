package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class StudyPlanGenerateFromGapDTO {

    @NotNull(message = "请选择能力画像")
    private Long profileId;

    private List<Long> gapItemIds;

    @Min(value = 1, message = "学习天数需在 1 到 60 天之间")
    @Max(value = 60, message = "学习天数需在 1 到 60 天之间")
    private Integer days;

    @Min(value = 15, message = "每天学习时长需在 15 到 480 分钟之间")
    @Max(value = 480, message = "每天学习时长需在 15 到 480 分钟之间")
    private Integer dailyMinutes;

    private LocalDate startDate;

    private String planTitle;
}
