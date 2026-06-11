package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SkillProfileGenerateDTO {

    @NotNull(message = "请选择匹配报告")
    private Long matchReportId;
}
