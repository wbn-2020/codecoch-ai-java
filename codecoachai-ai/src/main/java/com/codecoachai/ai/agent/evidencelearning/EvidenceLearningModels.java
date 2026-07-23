package com.codecoachai.ai.agent.evidencelearning;

import com.codecoachai.ai.domain.vo.EvidenceLearningCandidateDecisionVO;
import com.codecoachai.ai.domain.vo.EvidenceLearningSourceRefVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

public final class EvidenceLearningModels {

    private EvidenceLearningModels() {
    }

    @Data
    public static class CandidateQuery {
        private Long campaignId;
        private Long applicationId;
        private Long usageId;
        private String status;
    }

    @Data
    public static class DecisionCommand {
        private String decisionCode;
        private String idempotencyKey;
        private String editedContent;
    }

    @Data
    public static class CandidateView {
        private Long candidateId;
        private Long userId;
        private String candidateScopeType;
        private String candidateScopeKey;
        private String candidateType;
        private String candidateKey;
        private String title;
        private String content;
        private String usageSourceHash;
        private Integer evidenceCount;
        private Integer sampleCount;
        private List<String> limits = new ArrayList<>();
        private String confidenceLevel;
        private String status;
        private String decisionCode;
        private Long promotedMemoryId;
        private Boolean requiresUserConfirmation = true;
        private Boolean memoryEnabled = false;
        private String editDeepLink;
        private List<EvidenceLearningSourceRefVO> sourceRefs = new ArrayList<>();
        private List<String> availableDecisions = new ArrayList<>();
    }

    @Data
    public static class CandidateList {
        private List<CandidateView> candidates = new ArrayList<>();
        private LocalDateTime dataCutoffAt;
        private String sourceSetHash;
        private Map<String, Object> coverage = new LinkedHashMap<>();
        private String confidenceLevel;
        private List<String> unknowns = new ArrayList<>();
        private List<String> limits = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<EvidenceLearningSourceRefVO> sources = new ArrayList<>();
        private Boolean fallback = false;
        private String fallbackReason;
    }

    public static CandidateView from(
            com.codecoachai.ai.agent.campaignreview.domain.entity.CareerCampaignReviewMemoryCandidate candidate) {
        CandidateView view = new CandidateView();
        view.setCandidateId(candidate.getId());
        view.setUserId(candidate.getUserId());
        view.setCandidateScopeType(candidate.getCandidateScopeType());
        view.setCandidateScopeKey(candidate.getCandidateScopeKey());
        view.setCandidateType(candidate.getCandidateType());
        view.setCandidateKey(candidate.getCandidateKey());
        view.setTitle(candidate.getTitle());
        view.setContent(candidate.getContent());
        view.setUsageSourceHash(candidate.getUsageSourceHash());
        view.setEvidenceCount(candidate.getEvidenceCount());
        view.setSampleCount(candidate.getSampleCount());
        view.setConfidenceLevel(candidate.getConfidenceLevel());
        view.setStatus(candidate.getStatus());
        view.setDecisionCode(candidate.getDecisionCode());
        view.setPromotedMemoryId(candidate.getPromotedMemoryId());
        view.setMemoryEnabled(false);
        view.setRequiresUserConfirmation(
                !List.of("CONFIRMED", "CONFIRMED_BY_USER").contains(candidate.getStatus()));
        if (List.of("PENDING", "PENDING_CONFIRMATION", "WEAK_OBSERVATION")
                .contains(candidate.getStatus())) {
            view.setAvailableDecisions(List.of("KEEP", "EDIT", "CONTINUE", "REJECT"));
        }
        return view;
    }

    public static EvidenceLearningCandidateDecisionVO decision(
            String key, String title, String content, int usageCount, int sampleCount,
            String confidence, List<String> limits, List<EvidenceLearningSourceRefVO> refs) {
        EvidenceLearningCandidateDecisionVO decision = new EvidenceLearningCandidateDecisionVO();
        decision.setCandidateKey(key);
        decision.setTitle(title);
        decision.setContent(content);
        decision.setDecisionOptions(List.of("KEEP", "EDIT", "CONTINUE", "REJECT"));
        decision.setUsageCount(usageCount);
        decision.setSampleCount(sampleCount);
        decision.setConfidenceLevel(confidence);
        decision.setLimits(limits == null ? new ArrayList<>() : new ArrayList<>(limits));
        decision.setSourceRefs(refs == null ? new ArrayList<>() : new ArrayList<>(refs));
        decision.setRequiresUserConfirmation(true);
        return decision;
    }
}
