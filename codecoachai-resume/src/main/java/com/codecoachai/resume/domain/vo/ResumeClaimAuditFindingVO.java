package com.codecoachai.resume.domain.vo;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ResumeClaimAuditFindingVO {
    private Long id;
    private String sectionKey;
    private Integer claimIndex;
    private String claimType;
    private String claimText;
    private String claimHash;
    private List<String> quantities;
    private String evidenceStatus;
    private List<Map<String, Object>> evidenceRefs;
    private String reason;
}
