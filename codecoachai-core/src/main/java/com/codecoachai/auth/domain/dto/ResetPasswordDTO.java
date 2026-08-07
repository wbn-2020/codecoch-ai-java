package com.codecoachai.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    @NotBlank(message = "重置链接已失效，请重新获取")
    private String token;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "长度必须在8到32之间")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
