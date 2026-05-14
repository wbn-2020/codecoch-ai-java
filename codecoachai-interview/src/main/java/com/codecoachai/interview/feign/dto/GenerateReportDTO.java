package com.codecoachai.interview.feign.dto;

import java.util.List;
import lombok.Data;

@Data
public class GenerateReportDTO {

    private Long interviewId;
    private String mode;
    private String targetPosition;
    private String experienceLevel;
    private String industryDirection;
    private String difficulty;
    private String resumeContent;
    private String projectContent;
    private List<String> messages;
}
