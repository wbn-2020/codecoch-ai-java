package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_claim_audit")
public class ResumeClaimAudit extends BaseEntity {
    private Long userId;
    private Long resumeId;
    private Long resumeVersionId;
    private String sourceHash;
    private String auditVersion;
    private String status;
    private Integer claimCount;
    private Integer verifiedCount;
    private Integer partialCount;
    private Integer unsupportedCount;
    private Integer riskCount;
    private String errorMessage;
    private LocalDateTime completedAt;
}
