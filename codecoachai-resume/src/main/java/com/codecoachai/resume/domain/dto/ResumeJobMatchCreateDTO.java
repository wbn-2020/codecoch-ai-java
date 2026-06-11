package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResumeJobMatchCreateDTO {

    @NotNull(message = "请选择简历")
    private Long resumeId;

    @NotNull(message = "请选择目标岗位")
    private Long targetJobId;

    private Boolean forceRefresh;
}
