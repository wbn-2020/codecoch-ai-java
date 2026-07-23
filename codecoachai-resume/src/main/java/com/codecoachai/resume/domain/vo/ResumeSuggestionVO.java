package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ResumeSuggestionVO {
    private Long id;
    private Long resumeId;
    private Long sourceResumeVersionId;
    private String sourceType;
    private Long sourceId;
    private String sourceVersion;
    private String sectionKey;
    private String sectionId;
    private String fieldPath;
    private Integer anchorStart;
    private Integer anchorEnd;
    private String anchorTextHash;
    private String originalText;
    private String suggestedText;
    private String acceptedText;
    private List<Map<String, Object>> evidenceReferences;
    private String riskLevel;
    private String rationale;
    private String status;
    private Integer decisionVersion;
    private Long appliedResumeVersionId;
    private Long undoResumeVersionId;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private List<ResumeSuggestionDecisionVO> decisions;
}
