package com.codecoachai.interview.domain.vo;

import java.util.List;
import lombok.Data;

@Data
public class InterviewRemediationOptionsVO {

    private Long interviewId;
    private Long sourceReportId;
    private Long targetJobId;
    private String rubricVersion;
    private String trustStatus;
    private Boolean remediationAvailable;
    private String remediationUnavailableReason;
    private Boolean strongRemediationAvailable;
    private String strongRemediationUnavailableReason;
    private Boolean remediationCreated;
    private Long remediationId;
    private Long remediationTargetSessionId;
    private String remediationStatus;
    private List<InterviewRemediationOptionVO> options;
}
