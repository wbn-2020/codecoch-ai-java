package com.codecoachai.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    @NotBlank(message = "重置链接已失效，请重新获取")
    private String token;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    private String confirmPassword;
}
