package com.codecoachai.resume.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerEvidenceUsageQueryDTO {

    private Long pageNo = 1L;
    private Long pageSize = 20L;
    private Long usageId;
    private Long applicationId;
    private Long campaignId;
    private Long targetJobId;
    private String assetType;
    private Long assetId;
    private Long packageSnapshotId;
    private Long experimentId;
    private Long hypothesisId;
    private String status;
    private Boolean stale;
    private LocalDateTime dataCutoffAt;
}
