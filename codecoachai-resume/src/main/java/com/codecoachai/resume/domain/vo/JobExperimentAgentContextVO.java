package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class JobExperimentAgentContextVO {

    private Long id;
    private String title;
    private String targetDirection;
    private String status;
    private Integer sampleCount;
    private String confidenceLevel;
    private String sampleWarning;
    private String nextStrategy;
}
