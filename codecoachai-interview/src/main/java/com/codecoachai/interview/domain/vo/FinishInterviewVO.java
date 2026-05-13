package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class FinishInterviewVO {

    private Long id;
    private String status;
    private String reportStatus;
    private InterviewReportVO report;
}
