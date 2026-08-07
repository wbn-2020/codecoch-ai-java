package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InterviewRemediationVO {

    private Long id;
    private Long sourceReportId;
    private Long sourceSessionId;
    private Long targetSessionId;
    private Long targetJobId;
    private List<Long> sourceRequirementIds;
    private String practicePurpose;
    private String remediationStrength;
    private String rubricVersion;
    private String status;
    private Boolean idempotentReplay;
    private CreateInterviewVO interview;
}
