package com.codecoachai.ai.agent.service;

import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanChangePreviewDTO;
import com.codecoachai.ai.agent.domain.dto.AgentExternalPlanIntentDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanSuggestionIntentDTO;
import com.codecoachai.ai.agent.domain.entity.AgentReviewPlanSuggestion;
import com.codecoachai.ai.agent.domain.enums.AgentPlanSourceType;
import com.codecoachai.ai.agent.domain.vo.weekly.AgentWeeklyReportVO;
import com.codecoachai.ai.agent.domain.vo.weekly.WeeklyPlanDraftItemVO;
import com.codecoachai.ai.agent.service.support.AgentAdaptivePlanHashUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AgentPlanSourceAdapter {

    public List<AgentReviewPlanSuggestion> toSuggestions(
            Long userId,
            AgentExternalPlanChangePreviewDTO request) {
        AgentPlanSourceType sourceType = AgentPlanSourceType.parse(request.getSourceType());
        List<AgentReviewPlanSuggestion> result = new ArrayList<>();
        for (AgentExternalPlanIntentDTO intent : request.getIntents()) {
            AgentPlanSuggestionIntentDTO planIntent = new AgentPlanSuggestionIntentDTO();
            planIntent.setTaskType(intent.getTaskType());
            planIntent.setTitle(intent.getTitle());
            planIntent.setDescription(intent.getDescription());
            planIntent.setReason("来自 " + sourceType.name());
            planIntent.setPriority(intent.getPriority());
            planIntent.setEstimatedMinutes(intent.getEstimatedMinutes());
            planIntent.setRelatedSkillCode(intent.getRelatedSkillCode());
            planIntent.setRelatedSkillName(intent.getRelatedSkillName());
            planIntent.setRelatedBizType(intent.getRelatedBizType());
            planIntent.setRelatedBizId(intent.getRelatedBizId());
            planIntent.setActionUrl(intent.getActionUrl());

            AgentReviewPlanSuggestion suggestion = new AgentReviewPlanSuggestion();
            suggestion.setUserId(userId);
            suggestion.setSuggestionKey(intent.getSourceItemKey());
            suggestion.setSuggestionFingerprint(AgentAdaptivePlanHashUtils.sha256(
                    sourceType.name() + "|" + intent.getSourceItemKey() + "|" + intent.getTitle()));
            suggestion.setTitle(intent.getTitle());
            suggestion.setContent(intent.getDescription());
            suggestion.setReason(planIntent.getReason());
            suggestion.setIntentType("ADD_PRACTICE");
            suggestion.setTargetScope(AgentAdaptivePlanHashUtils.targetScopeKey(request.getTargetJobId()));
            suggestion.setIntentJson(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(planIntent).toString());
            suggestion.setConfidenceLevel(intent.getConfidenceLevel());
            suggestion.setFallback(Boolean.TRUE.equals(intent.getFallback()));
            suggestion.setDecisionStatus("ACCEPTED");
            suggestion.setSourceType(sourceType.name());
            suggestion.setSourceId(request.getSourceId());
            suggestion.setSourceVersion(request.getSourceVersion());
            suggestion.setSourceSnapshotHash(request.getSourceContextHash());
            suggestion.setSourceItemKey(intent.getSourceItemKey());
            result.add(suggestion);
        }
        return result;
    }

    public String contextHash(AgentExternalPlanChangePreviewDTO request) {
        return AgentAdaptivePlanHashUtils.sha256(List.of(
                request.getSourceType(),
                Objects.toString(request.getSourceId(), ""),
                Objects.toString(request.getSourceVersion(), ""),
                request.getSourceContextHash(),
                request.getIntents().stream().map(AgentExternalPlanIntentDTO::getSourceItemKey)
                        .collect(Collectors.joining(","))).toString());
    }

    public AgentExternalPlanChangePreviewDTO fromWeeklyReport(
            AgentWeeklyReportVO report,
            String idempotencyKey) {
        if (report == null || report.getSnapshotId() == null || report.getPlanDraft() == null) {
            throw new IllegalArgumentException("weekly report plan draft is required");
        }
        AgentExternalPlanChangePreviewDTO request = new AgentExternalPlanChangePreviewDTO();
        request.setSourceType(AgentPlanSourceType.WEEKLY_REPORT.name());
        request.setSourceId(report.getSnapshotId());
        request.setSourceVersion(report.getSnapshotVersion());
        request.setSourceContextHash(firstText(
                report.getPlanDraft().getSourceSnapshotId(),
                AgentAdaptivePlanHashUtils.sha256(report.getSnapshotId() + "|" + report.getGeneratedAt())));
        request.setTargetJobId(report.getTargetJobId());
        request.setIdempotencyKey(idempotencyKey);
        List<AgentExternalPlanIntentDTO> intents = new ArrayList<>();
        for (WeeklyPlanDraftItemVO item : report.getPlanDraft().getItems()) {
            if (item == null || !StringUtils.hasText(item.getSemanticKey())) {
                continue;
            }
            AgentExternalPlanIntentDTO intent = new AgentExternalPlanIntentDTO();
            intent.setSourceItemKey(item.getSemanticKey());
            intent.setTitle(item.getTitle());
            intent.setDescription(item.getDescription());
            intent.setPlanDate(item.getTargetDate());
            intent.setEstimatedMinutes(item.getEstimatedMinutes());
            intent.setPriority(item.getPriority());
            intent.setRelatedBizType("AGENT_WEEKLY_REPORT_SNAPSHOT");
            intent.setRelatedBizId(report.getSnapshotId());
            intents.add(intent);
        }
        request.setIntents(intents);
        return request;
    }

    public AgentExternalPlanChangePreviewDTO fromInterviewPreparation(
            Long preparationId,
            Integer version,
            String sourceHash,
            Long targetJobId,
            List<AgentExternalPlanIntentDTO> nextActions,
            String idempotencyKey) {
        AgentExternalPlanChangePreviewDTO request = new AgentExternalPlanChangePreviewDTO();
        request.setSourceType(AgentPlanSourceType.INTERVIEW_PREPARATION.name());
        request.setSourceId(preparationId);
        request.setSourceVersion(version);
        request.setSourceContextHash(sourceHash);
        request.setTargetJobId(targetJobId);
        request.setIntents(nextActions == null ? List.of() : new ArrayList<>(nextActions));
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : fallback;
    }
}
