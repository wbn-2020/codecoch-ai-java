package com.codecoachai.ai.agent.domain.vo.review;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentPlanChangeConfirmVO {

    private Long changeSetId;
    private String status;
    private LocalDateTime confirmedAt;
    private LocalDateTime appliedAt;
    private Long dailyPlanRunId;
    private Long weekPlanId;
    private Integer weekSnapshotVersion;
    private Integer appliedItemCount = 0;
    private Integer waitingItemCount = 0;
    private List<String> conflicts = new ArrayList<>();
    private String message;
}
