package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResumeJobMatchCreateDTO {

    @NotNull(message = "resumeId is required")
    private Long resumeId;

    @NotNull(message = "targetJobId is required")
    private Long targetJobId;

    private Boolean forceRefresh;
}
