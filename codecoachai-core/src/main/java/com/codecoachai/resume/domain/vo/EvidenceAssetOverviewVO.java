package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class EvidenceAssetOverviewVO {

    private Long assetCount = 0L;
    private Long versionedAssetCount = 0L;
    private Long usageCount = 0L;
    private Long outcomeSampleCount = 0L;
    private Long pendingCandidateCount = 0L;
    private List<ReadinessItem> readiness = new ArrayList<>();

    @Data
    public static class ReadinessItem {
        private String assetType;
        private String label;
        private Long totalCount = 0L;
        private Long versionedCount = 0L;
        private Long usedCount = 0L;
        private Long resultCount = 0L;
        private Long staleCount = 0L;
        private String readinessStatus;
        private String readinessReason;
        private String actionPath;
    }
}
