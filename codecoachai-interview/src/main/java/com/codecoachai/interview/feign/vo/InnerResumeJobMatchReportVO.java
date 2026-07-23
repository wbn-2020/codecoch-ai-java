package com.codecoachai.interview.feign.vo;

import lombok.Data;

@Data
public class InnerResumeJobMatchReportVO {

    private Long reportId;
    private Long userId;
    private Long resumeId;
    private Long targetJobId;
    private Long resumeVersionId;
    private Long jdAnalysisId;
    private String status;
}
