package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewReplayVO {

    private Long id;
    private Long sourceSessionId;
    private Long sourceReportId;
    private Long targetSessionId;
    private Long targetJobId;
    private Long scenarioVersionId;
    private String rubricVersion;
    private String status;
    private Boolean idempotentReplay;
    private CreateInterviewVO interview;
}
