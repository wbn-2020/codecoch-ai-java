package com.codecoachai.resume.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobReadinessPageQueryDTO {

    @NotNull(message = "pageNo is required")
    @Min(value = 1, message = "pageNo must be at least 1")
    private Long pageNo;

    @NotNull(message = "pageSize is required")
    @Min(value = 1, message = "pageSize must be at least 1")
    @Max(value = 100, message = "pageSize must not exceed 100")
    private Long pageSize;
}
