package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class InterviewRemediationCreateDTO {

    @NotNull
    @Positive
    private Long sourceReportId;

    @Size(max = 20)
    private List<@NotNull @Positive Long> sourceRequirementIds;

    @NotBlank
    @Size(max = 500)
    private String practicePurpose;

    private Boolean strongRemediation;

    @NotBlank
    @Size(max = 64)
    @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$",
            message = "幂等键仅支持1到64位ASCII字母、数字、点、下划线、冒号或连字符，且必须以字母或数字开头")
    private String idempotencyKey;
}
