package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class StudyPlanDetailVO {

    private Long id;
    private Long reportId;
    private Long sessionId;
    private Long resumeId;
    private Long optimizeRecordId;
    private String sourceType;
    private String targetPosition;
    private String industryDirection;
    private String planTitle;
    private String planSummary;
    private String planStatus;
    private Integer durationDays;
    private Long aiCallLogId;
    private String failureReason;
    private Integer totalTaskCount;
    private Integer doneTaskCount;
    private Integer progressPercent;
    private List<StudyTaskVO> tasks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
