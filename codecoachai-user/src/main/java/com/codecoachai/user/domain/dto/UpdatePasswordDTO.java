package com.codecoachai.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePasswordDTO {

    @NotBlank(message = "不能为空")
    private String oldPassword;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度必须在6到32之间")
    private String newPassword;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度必须在6到32之间")
    private String confirmPassword;
}
