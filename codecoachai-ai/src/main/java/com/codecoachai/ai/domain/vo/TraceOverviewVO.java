package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TraceOverviewVO {

    private String queryId;
    private String resolvedLookupType;
    private String primaryTraceId;
    private List<String> traceIds = new ArrayList<>();
    private Integer sampleCount = 0;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime queryStartTime;
    private LocalDateTime queryEndTime;
    private Boolean defaultTimeWindowApplied = false;
    private Integer queryLimit;
    private Integer maxLimit;
    private Integer lowConfidenceCount = 0;
    private Integer aiCallCount;
    private Integer agentRunCount;
    private Integer agentTaskCount;
    private Integer agentWeekPlanCount;
    private Integer agentWeekPlanItemCount;
    private Integer asyncTaskCount;
    private Integer applicationPackageCount;
    private Integer interviewSessionCount;
    private Integer interviewReportCount;
    private Integer interviewVoiceCount;
    private Integer failedCount;
    private Integer fallbackCount;
    private Long totalTokens;
    private Long maxElapsedMs;
    private Boolean rawFieldsAvailable = false;
    private Boolean rawFieldsIncluded = false;
    private String rawAccessPermission;
    private String healthStatus = "UNKNOWN";
    private Boolean partialResult = false;
    private List<TraceModuleStatusVO> moduleStatuses = new ArrayList<>();
}
