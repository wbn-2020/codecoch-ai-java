package com.codecoachai.ai.agent.domain.context;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class AgentReviewPlanWeekResult {

    private Long weekPlanId;
    private Integer snapshotVersion;
    private Map<Long, Long> weekPlanItemIdsByTaskId = new LinkedHashMap<>();
}
