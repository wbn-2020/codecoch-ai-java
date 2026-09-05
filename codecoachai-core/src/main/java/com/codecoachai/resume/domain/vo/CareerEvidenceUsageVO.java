package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CareerEvidenceUsageVO {

    private Long id;
    private Long userId;
    private Long campaignId;
    private Long applicationId;
    private Long targetJobId;
    private String assetType;
    private Long assetId;
    private String assetVersion;
    private Long packageSnapshotId;
    private String sourceHash;
    private String contentHash;
    private String usageScene;
    private LocalDateTime usedAt;
    private Long hypothesisId;
    private Long variantId;
    private Long assignmentId;
    private String usageKeyHash;
    private String idempotencyKeyHash;
    private String status;
    private Boolean stale;
    private String staleReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LocalDateTime dataCutoffAt;
    private String sourceSetHash;
    private Map<String, Object> coverage = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> unknowns = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private String confidenceLevel;
    private Boolean fallback;
    private List<SourceRef> sources = new ArrayList<>();

    @Data
    public static class SourceRef {
        private String sourceType;
        private Long sourceId;
        private String sourceVersion;
        private String sourceHash;
        private String fieldPath;
        private String summary;
        private LocalDateTime observedAt;
        private LocalDateTime sourceUpdatedAt;
    }
}
