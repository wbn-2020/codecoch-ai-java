package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String idempotencyKey;
}
