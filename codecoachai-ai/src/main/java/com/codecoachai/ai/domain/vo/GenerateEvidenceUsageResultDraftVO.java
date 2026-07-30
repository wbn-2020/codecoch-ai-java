package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateEvidenceUsageResultDraftVO {
    private String scene = "EVIDENCE_USAGE_RESULT_DRAFT_V9";
    private String promptVersion = "v9-1";
    private String schemaVersion = "V9_EVIDENCE_USAGE_OUTPUT_V1";
    private LocalDateTime dataCutoffAt;
    private String sourceSetHash;
    private String summary;
    private List<String> facts = new ArrayList<>();
    private List<String> weakObservations = new ArrayList<>();
    private List<String> unknowns = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private List<EvidenceLearningCandidateDecisionVO> candidateDecision = new ArrayList<>();
    private EvidenceLearningReuseDraftVO reuseDraft;
    private List<EvidenceLearningSourceRefVO> sourceRefs = new ArrayList<>();
    private String confidenceLevel;
    private Boolean fallback = false;
    private String fallbackReason;
    private Long aiCallLogId;
}
