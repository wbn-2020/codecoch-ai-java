package com.codecoachai.interview.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewReplayCreateDTO {

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键长度不能超过64")
    private String idempotencyKey;
}
