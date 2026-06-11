package com.codecoachai.question.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusDTO {

    @NotNull(message = "请选择状态")
    private Integer status;
}
