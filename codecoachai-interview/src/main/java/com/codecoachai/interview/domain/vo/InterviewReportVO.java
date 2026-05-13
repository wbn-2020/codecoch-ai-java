package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewReportVO {

    private Long id;
    private Long sessionId;
    private String status;
    private Integer totalScore;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String suggestions;
    private String failureReason;
}
