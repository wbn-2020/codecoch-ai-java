package com.codecoachai.ai.agent.domain.vo.weekly;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class WeeklyPlanDraftVO {

    private Boolean available = false;
    private String sourceSnapshotId;
    private String targetWeekStart;
    private String unavailableReason;
    private List<WeeklyPlanDraftItemVO> items = new ArrayList<>();
    private String stageFivePreviewRoute;
}
