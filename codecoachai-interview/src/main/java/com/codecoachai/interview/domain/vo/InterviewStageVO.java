package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewStageVO {

    private Long id;
    private String stageType;
    private String stageName;
    private Integer sort;
    private String status;
}
