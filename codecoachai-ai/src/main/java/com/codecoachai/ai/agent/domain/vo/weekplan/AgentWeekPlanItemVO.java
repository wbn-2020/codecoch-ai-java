package com.codecoachai.ai.agent.domain.vo.weekplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentWeekPlanItemVO {
    private Long id;
    private Long weekPlanId;
    private String layer;
    private String actionType;
    private String title;
    private String description;
    private String reason;
    private String relatedBizType;
    private Long relatedBizId;
    private String relatedBizTitle;
    private Long agentTaskId;
    private String priority;
    private BigDecimal confidence;
    private String confidenceLevel;
    private String trustStatus;
    private Boolean fallback;
    private String fallbackReason;
    private String traceId;
    private Integer snapshotVersion;
    private Boolean sampleInsufficient;
    private String sampleWarning;
    private String itemStatus;
    private LocalDate plannedDate;
    private LocalDate dueDate;
    private String actionUrl;
    private List<String> evidence = new ArrayList<>();
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
