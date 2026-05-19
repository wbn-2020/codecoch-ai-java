package com.codecoachai.interview.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class GenerateReportDTO {

    private Long interviewId;
    private Long userId;
    private Long targetJobId;
    private Long skillProfileId;
    private Long matchReportId;
    private String skillGapContext;
    private String mode;
    private String targetPosition;
    private String experienceLevel;
    private String industryDirection;
    private String industryContext;
    private String difficulty;
    private String resumeContent;
    private String projectContent;
    private List<String> messages;
}
