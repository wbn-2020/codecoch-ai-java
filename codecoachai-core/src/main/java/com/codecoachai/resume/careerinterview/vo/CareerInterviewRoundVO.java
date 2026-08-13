package com.codecoachai.resume.careerinterview.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerInterviewRoundVO {
    private Long id;
    private Long processId;
    private Integer roundNo;
    private String roundType;
    private String title;
    private String timezone;
    private LocalDateTime scheduledStartsAtUtc;
    private LocalDateTime scheduledEndsAtUtc;
    private Long calendarEventId;
    private String preparationSourceHash;
    private Boolean preparationStale;
    private String status;
    private String resultSummary;
    private String nextStep;
    private Integer lockVersion;
    private List<CareerInterviewRoundEventVO> events = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
