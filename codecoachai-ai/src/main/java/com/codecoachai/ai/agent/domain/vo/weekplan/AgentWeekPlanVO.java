package com.codecoachai.ai.agent.domain.vo.weekplan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentWeekPlanVO {
    private Long id;
    private Long targetJobId;
    private Long agentRunId;
    private LocalDate planDate;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String planStatus;
    private String summary;
    private String focusJson;
    private String traceId;
    private String resultSource;
    private Boolean fallback;
    private String fallbackReason;
    private Integer snapshotVersion;
    private String dataSource = "BACKEND_PERSISTED";
    private LocalDateTime generatedAt;
    private LocalDateTime refreshedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AgentWeekPlanItemVO> items = new ArrayList<>();
}
