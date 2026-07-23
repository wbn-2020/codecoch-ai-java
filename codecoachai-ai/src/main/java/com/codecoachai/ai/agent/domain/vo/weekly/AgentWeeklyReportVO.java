package com.codecoachai.ai.agent.domain.vo.weekly;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentWeeklyReportVO {

    private Long id;
    private Long snapshotId;
    private Long targetJobId;
    private String targetScopeKey;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String timezone;
    private String reportStatus;
    private Integer snapshotVersion;
    private String operationResult;
    private String summary;
    private String confidenceLevel;
    private Boolean fallback;
    private String fallbackReason;
    private String resultSource;
    private String traceId;
    private Long aiCallLogId;
    private WeeklyReportRangeVO range;
    private WeeklyReportCoverageVO coverage;
    private List<WeeklyReportFactVO> facts = new ArrayList<>();
    private List<WeeklyReportSignalVO> signals = new ArrayList<>();
    private List<WeeklyReportHypothesisVO> hypotheses = new ArrayList<>();
    private List<WeeklyExperimentSuggestionVO> experimentSuggestions = new ArrayList<>();
    private WeeklyPlanDraftVO planDraft;
    private List<WeeklyReportSnapshotVersionVO> snapshotHistory = new ArrayList<>();
    private LocalDateTime sourceCutoffAt;
    private LocalDateTime generatedAt;
    private LocalDateTime refreshedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
