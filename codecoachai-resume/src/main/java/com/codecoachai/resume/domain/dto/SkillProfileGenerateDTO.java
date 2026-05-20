package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SkillProfileGenerateDTO {

    @NotNull(message = "matchReportId cannot be null")
    private Long matchReportId;
}
