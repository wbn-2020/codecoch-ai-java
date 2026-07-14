package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class InterviewComparisonCreateDTO {

    @NotEmpty
    @Size(min = 2, max = 10)
    private List<@NotNull @Positive Long> reportIds;

    @NotBlank
    @Size(max = 64)
    private String idempotencyKey;
}
