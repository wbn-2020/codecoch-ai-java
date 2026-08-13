package com.codecoachai.resume.service;

import com.codecoachai.resume.domain.vo.ResumeClaimAuditVO;
import java.util.List;

public interface ResumeClaimAuditService {
    ResumeClaimAuditVO audit(Long resumeVersionId);

    ResumeClaimAuditVO detail(Long auditId);

    List<ResumeClaimAuditVO> list(Long resumeId);
}
