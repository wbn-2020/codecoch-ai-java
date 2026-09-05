package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_claim_audit_finding")
public class ResumeClaimAuditFinding extends BaseEntity {
    private Long auditId;
    private Long userId;
    private String sectionKey;
    private Integer claimIndex;
    private String claimType;
    private String claimText;
    private String claimHash;
    private String quantitiesJson;
    private String evidenceStatus;
    private String evidenceRefsJson;
    private String reason;
}
