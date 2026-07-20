package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentPlanChangeConfirmDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentReviewPlanDecisionDTO;
import com.codecoachai.ai.agent.domain.entity.AgentReview;
import com.codecoachai.ai.agent.domain.entity.AgentTask;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangeConfirmVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentPlanChangePreviewVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewPlanDecisionSummaryVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewPlanSuggestionListVO;
import com.codecoachai.ai.agent.domain.vo.review.AgentReviewPlanSuggestionVO;
import java.time.LocalDate;
import java.util.List;

public interface AgentReviewPlanService {

    AgentReviewPlanSuggestionListVO suggestions(Long userId, Long reviewId);

    AgentReviewPlanSuggestionListVO decide(Long userId, Long reviewId, AgentReviewPlanDecisionDTO dto);

    AgentPlanChangePreviewVO preview(Long userId, Long reviewId, AgentPlanChangePreviewDTO dto);

    AgentPlanChangePreviewVO changeSet(Long userId, Long changeSetId);

    List<AgentPlanChangePreviewVO> changeSets(Long userId, LocalDate targetDate, String statuses);

    AgentPlanChangeConfirmVO confirm(Long userId, Long changeSetId, AgentPlanChangeConfirmDTO dto);

    void materializeSuggestions(AgentReview review, List<AgentTask> sourceTasks, List<String> adjustments);

    ReviewGenerationClaim claimDailyReview(AgentReview review);

    AgentReview completeClaimedDailyReview(ReviewGenerationClaim claim,
                                           AgentReview review,
                                           List<AgentTask> sourceTasks,
                                           List<String> adjustments);

    AgentReview persistReviewWithSuggestions(AgentReview review,
                                             List<AgentTask> sourceTasks,
                                             List<String> adjustments);

    List<AgentReviewPlanSuggestionVO> suggestionVOs(Long userId, Long reviewId);

    AgentReviewPlanDecisionSummaryVO decisionSummary(Long userId, Long reviewId);

    record ReviewGenerationClaim(
            AgentReview current,
            String requestedSourceSnapshotHash,
            Integer previousReviewVersion,
            String previousSourceSnapshotHash,
            boolean shouldGenerate,
            boolean newlyClaimed) {
    }
}
