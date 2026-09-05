package com.codecoachai.interview.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "Study plan daily task view")
public class StudyPlanDailyViewVO {

    @Schema(description = "Study plan id")
    private Long planId;

    @Schema(description = "Study plan title")
    private String planTitle;

    @Schema(description = "Persisted study plan status")
    private String planStatus;

    @Schema(description = "Derived schedule status such as ACTIVE, NOT_STARTED, EXPIRED or UNSCHEDULED")
    private String scheduleStatus;

    @Schema(description = "Effective end date derived from scheduled tasks or the configured duration")
    private LocalDate endDate;

    @Schema(description = "Whether the active plan has no remaining scheduled day")
    private Boolean expired;

    @Schema(description = "Whether the expired plan can be renewed without changing the old plan")
    private Boolean renewable;

    @Schema(description = "Requested date")
    private LocalDate date;

    @Schema(description = "Day index inferred from the plan start date, starting from 1")
    private Integer dayIndex;

    @Schema(description = "Total task count for this day")
    private Integer totalTaskCount;

    @Schema(description = "Pending task count for this day")
    private Integer pendingTaskCount;

    @Schema(description = "Completed task count for this day")
    private Integer completedTaskCount;

    @Schema(description = "Skipped task count for this day")
    private Integer skippedTaskCount;

    @Schema(description = "Completion rate percent for this day")
    private Integer completionRate;

    @Schema(description = "Tasks for this day")
    private List<StudyTaskVO> tasks;
}
