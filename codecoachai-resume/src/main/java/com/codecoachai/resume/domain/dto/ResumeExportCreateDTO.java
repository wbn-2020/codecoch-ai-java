package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeExportCreateDTO {
    @NotNull
    private Long resumeVersionId;
    @Size(max = 64)
    private String templateCode;
    private Integer templateVersion;
    @NotBlank
    @Size(max = 16)
    private String format;
}
