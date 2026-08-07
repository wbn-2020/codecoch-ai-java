package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewReportGenerateResultVO {

    private Long interviewId;
    private Long reportId;
    private Long aiCallLogId;
    private InterviewReportVO result;
}
