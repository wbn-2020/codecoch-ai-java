package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeArtifactPackageCreateDTO {
    @NotNull
    private Long resumeVersionId;
    @NotNull
    private Long applicationPackageId;
    @Size(max = 64)
    private String templateCode;
    private Integer templateVersion;
}
