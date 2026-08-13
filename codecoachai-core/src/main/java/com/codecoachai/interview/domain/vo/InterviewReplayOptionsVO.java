package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewReplayOptionsVO {

    private Long interviewId;
    private Long sourceReportId;
    private Long targetJobId;
    private Long scenarioVersionId;
    private String rubricVersion;
    private String state;
    private Boolean replayAvailable;
    private String reasonCode;
    private String reasonMessage;
    private String policyVersion;
}
