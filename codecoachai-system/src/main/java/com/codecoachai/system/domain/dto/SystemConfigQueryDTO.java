package com.codecoachai.system.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SystemConfigQueryDTO {

    @Min(1)
    private Long pageNo = 1L;

    @Min(1)
    @Max(100)
    private Long pageSize = 10L;

    private String keyword;

    private String configType;

    private Integer status;
}
