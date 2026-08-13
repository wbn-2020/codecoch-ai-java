package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResumeClaimAuditCreateDTO {
    @NotNull
    private Long resumeVersionId;
}
