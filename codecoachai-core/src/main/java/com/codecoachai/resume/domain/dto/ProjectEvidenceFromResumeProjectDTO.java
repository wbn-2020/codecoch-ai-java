package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectEvidenceFromResumeProjectDTO {

    @NotNull(message = "sourceResumeId is required")
    private Long sourceResumeId;

    @NotNull(message = "sourceResumeProjectId is required")
    private Long sourceResumeProjectId;

    private Long targetJobId;
}
