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
    private List<InterviewRemediationOptionVO> options;
}
