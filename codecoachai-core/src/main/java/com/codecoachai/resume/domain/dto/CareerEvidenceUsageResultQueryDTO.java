package com.codecoachai.resume.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerEvidenceUsageResultQueryDTO {

    private Long pageNo = 1L;
    private Long pageSize = 20L;
    private Long resultId;
    private Long campaignId;
    private Long applicationId;
    private Long targetJobId;
    private Long experimentId;
    private Long hypothesisId;
    private Long usageId;
    private String assetType;
    private Long assetId;
    private Long packageSnapshotId;
    private String status;
    private String outcomeCode;
    private LocalDateTime dataCutoffAt;
}
