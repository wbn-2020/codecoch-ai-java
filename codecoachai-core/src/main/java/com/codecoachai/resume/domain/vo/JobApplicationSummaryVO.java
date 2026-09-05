package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class JobApplicationSummaryVO {
    private Long id;
    private Long userId;
    private Long targetJobId;
    private Long resumeVersionId;
    private Long matchReportId;
    private String status;
}
