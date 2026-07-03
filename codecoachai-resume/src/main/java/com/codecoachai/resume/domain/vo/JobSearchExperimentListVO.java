package com.codecoachai.resume.domain.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobSearchExperimentListVO {

    private Long id;
    private String title;
    private String goal;
    private String targetDirection;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer sampleCount;
    private String confidenceLevel;
    private String sampleWarning;
    private String summary;
    private String nextStrategy;
    private Integer demoFlag;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private JobSearchExperimentMetricsVO metrics;
}
