package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class StudyPlanGenerateVO {

    private Long planId;
    private String planStatus;
    private String planTitle;
    private Integer taskCount;
    private Integer skillGapCount;
    private Long aiCallLogId;
    private String asyncMessageId;
    private String asyncTraceId;
    private String asyncBizType;
    private String asyncBizId;
    private String asyncSendStatus;
    private String failureReason;
}
