package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewReplayCreateDTO {

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键长度不能超过64")
    @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$",
            message = "幂等键仅支持1到64位ASCII字母、数字、点、下划线、冒号或连字符，且必须以字母或数字开头")
    private String idempotencyKey;
}
