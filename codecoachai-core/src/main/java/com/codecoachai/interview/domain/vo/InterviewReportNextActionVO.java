package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewReportNextActionVO {

    private String actionType;
    private String title;
    private String description;
    private Integer priority;
    private String actionUrl;
    private String relatedBizType;
    private Long relatedBizId;
    private String evidence;
}
