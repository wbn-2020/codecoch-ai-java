package com.codecoachai.ai.domain.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class GenerateTargetedStudyPlanDTO {

    private Long learningPlanId;
    private Long userId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private String targetJobJson;
    private String skillProfileJson;
    private String skillGapsJson;
    private Integer availableDays;
    private Integer dailyMinutes;
    private LocalDate startDate;
    private String existingStudyPlansJson;
    private String planTitle;
}
