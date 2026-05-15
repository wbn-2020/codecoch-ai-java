package com.codecoachai.resume.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class ResumeOptimizeRequestDTO {

    private String targetPosition;
    private Integer experienceYears;
    private String industryDirection;
    private List<Long> selectedProjectIds;
}
