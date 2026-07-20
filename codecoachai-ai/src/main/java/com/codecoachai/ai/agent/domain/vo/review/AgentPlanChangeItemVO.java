package com.codecoachai.ai.agent.domain.vo.review;

import com.codecoachai.ai.agent.domain.dto.AgentPlanTaskSnapshotDTO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentPlanChangeItemVO {

    private Long id;
    private String itemKey;
    private String changeType;
    private String title;
    private LocalDate targetDate;
    private AgentPlanTaskSnapshotDTO before;
    private AgentPlanTaskSnapshotDTO after;
    private String dailyImpact;
    private String weekImpact;
    private Long sourceReviewId;
    private Long sourceSuggestionId;
    private String validationStatus;
    private String confidenceLevel;
    private Boolean fallback;
    private String applyStatus;
    private List<String> warnings = new ArrayList<>();
}
