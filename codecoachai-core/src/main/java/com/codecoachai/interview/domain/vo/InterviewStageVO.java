package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewStageVO {

    private Long id;
    private String stageType;
    private String stageName;
    private Integer sort;
    private Integer stageOrder;
    private Integer expectedQuestionCount;
    private Integer askedQuestionCount;
    private String focusPoints;
    private Boolean basedOnResume;
    private Boolean allowFollowUp;
    private Integer maxFollowUpCount;
    private String status;
    private Integer score;
}
