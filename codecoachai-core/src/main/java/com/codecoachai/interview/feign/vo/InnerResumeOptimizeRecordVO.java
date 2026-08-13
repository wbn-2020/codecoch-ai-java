package com.codecoachai.interview.feign.vo;

import lombok.Data;

@Data
public class InnerResumeOptimizeRecordVO {

    private Long optimizeRecordId;
    private Long userId;
    private Long resumeId;
    private String targetPosition;
    private Integer experienceYears;
    private String industryDirection;
    private String optimizeStatus;
    private String resultJson;
    private String errorMessage;
}
