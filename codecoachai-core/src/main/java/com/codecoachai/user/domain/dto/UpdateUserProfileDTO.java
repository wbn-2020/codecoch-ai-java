package com.codecoachai.user.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserProfileDTO {

    @Size(max = 50, message = "长度不能超过50")
    private String nickname;

    @Size(max = 255, message = "长度不能超过255")
    private String avatarUrl;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "长度不能超过100")
    private String email;
}
