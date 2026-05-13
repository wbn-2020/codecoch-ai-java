package com.codecoachai.user.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusDTO {

    @NotNull(message = "不能为空")
    private Integer status;
}
