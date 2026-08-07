package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_evidence_usage_result")
public class CareerEvidenceUsageResult extends BaseEntity {

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
}
