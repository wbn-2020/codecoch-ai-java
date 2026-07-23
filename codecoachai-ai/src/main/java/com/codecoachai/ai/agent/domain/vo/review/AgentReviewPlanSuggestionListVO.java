package com.codecoachai.ai.agent.domain.vo.review;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentReviewPlanSuggestionListVO {

    private Long reviewId;
    private Integer reviewVersion;
    private LocalDate reviewDate;
    private String sourceSnapshotHash;
    private List<AgentReviewPlanSuggestionVO> suggestions = new ArrayList<>();
    private AgentReviewPlanDecisionSummaryVO decisionSummary = new AgentReviewPlanDecisionSummaryVO();
}
