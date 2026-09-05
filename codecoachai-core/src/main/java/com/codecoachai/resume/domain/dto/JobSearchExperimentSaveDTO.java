package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class JobSearchExperimentSaveDTO {

    @NotBlank(message = "Experiment title is required")
    private String title;
    private Long targetJobId;
    private Long resumeVersionId;
    private List<Long> targetJobIds;
    private List<Long> resumeIds;
    private String goal;
    private String targetDirection;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Boolean demoFlag;
}
