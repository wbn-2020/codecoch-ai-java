package com.codecoachai.user.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminUserQueryDTO {

    private String keyword;
    private Integer status;
    private String roleCode;

    @Min(value = 1, message = "最小为1")
    private Long pageNo = 1L;

    @Min(value = 1, message = "最小为1")
    @Max(value = 100, message = "最大为100")
    private Long pageSize = 10L;
}
