package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_suggestion")
public class ResumeSuggestion extends BaseEntity {
    private Long userId;
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
    private String evidenceRefsJson;
    private String riskLevel;
    private String rationale;
    private String status;
    private Integer decisionVersion;
    private Long appliedResumeVersionId;
    private Long undoResumeVersionId;
    private LocalDateTime decidedAt;
}
