package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_evidence_usage")
public class CareerEvidenceUsage extends BaseEntity {

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
    private String idempotencyPayloadHash;
    private String status;
    private Integer stale;
    private String staleReason;
}
