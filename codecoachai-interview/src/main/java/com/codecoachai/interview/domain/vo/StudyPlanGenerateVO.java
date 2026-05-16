package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class StudyPlanGenerateVO {

    private Long planId;
    private String planStatus;
    private String planTitle;
    private String failureReason;
}
