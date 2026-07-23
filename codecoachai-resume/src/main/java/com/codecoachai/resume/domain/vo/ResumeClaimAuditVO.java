package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ResumeClaimAuditVO {
    private Long id;
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
    private LocalDateTime createdAt;
    private List<ResumeClaimAuditFindingVO> findings;
}
