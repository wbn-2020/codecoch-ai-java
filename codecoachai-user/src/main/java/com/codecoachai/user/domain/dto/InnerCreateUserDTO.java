package com.codecoachai.user.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InnerCreateUserDTO {

    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "只能包含字母、数字、下划线，长度4到32")
    private String username;

    @NotBlank(message = "不能为空")
    private String passwordHash;

    @Size(max = 50, message = "长度不能超过50")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "长度不能超过100")
    private String email;
}
