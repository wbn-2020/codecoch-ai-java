package com.codecoachai.ai.agent.domain.vo.weekly;

import java.time.LocalDate;
import lombok.Data;

@Data
public class WeeklyPlanDraftItemVO {

    private String semanticKey;
    private LocalDate targetDate;
    private String actionType;
    private String title;
    private String description;
    private String reason;
    private String sourceWeeklyReportSnapshotId;
    private String sourceHypothesisId;
    private Integer estimatedMinutes;
    private String priority;
    private Boolean conflictCheckRequired = true;
    private String userDecision;
    private Boolean requiresUserConfirmation = true;
}
