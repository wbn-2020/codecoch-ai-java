package com.codecoachai.interview.feign.vo;

import lombok.Data;

@Data
public class InnerJobApplicationSummaryVO {
    private Long id;
    private Long userId;
    private Long targetJobId;
    private Long resumeVersionId;
    private Long matchReportId;
    private String status;
}
