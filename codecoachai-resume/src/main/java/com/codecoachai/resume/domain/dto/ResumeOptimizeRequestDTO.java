package com.codecoachai.resume.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class ResumeOptimizeRequestDTO {

    private String targetPosition;
    private Integer experienceYears;
    private String industryDirection;
    private String targetCompany;
    private String extraRequirements;
    private String optimizeFocus;
    private List<Long> selectedProjectIds;
}
