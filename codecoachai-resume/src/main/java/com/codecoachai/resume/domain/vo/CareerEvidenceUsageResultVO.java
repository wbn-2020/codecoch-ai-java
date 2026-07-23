package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CareerEvidenceUsageResultVO {

    private Long id;
    private Long userId;
    private Long usageId;
    private Long applicationId;
    private String eventType;
    private Long eventId;
    private String eventKeyHash;
    private Long currentSnapshotId;
    private Integer snapshotVersion;
    private String status;
    private Integer lockVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String outcomeCode;
    private List<String> knownFacts = new ArrayList<>();
    private String externalFeedbackText;
    private String userInterpretationText;
    private List<String> unknowns = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private String sourceType;
    private Long sourceId;
    private String sourceVersion;
    private String sourceHash;
    private LocalDateTime occurredAt;
    private LocalDateTime confirmedAt;
    private String contentHash;
    private Long supersedesSnapshotId;

    private LocalDateTime dataCutoffAt;
    private String sourceSetHash;
    private Map<String, Object> coverage = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> unknownsFromCoverage = new ArrayList<>();
    private List<String> limitsFromCoverage = new ArrayList<>();
    private String confidenceLevel;
    private Boolean fallback;
    private List<CareerEvidenceUsageVO.SourceRef> sources = new ArrayList<>();
}
