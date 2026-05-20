package com.codecoachai.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "newPassword is required")
    private String newPassword;

    private String confirmPassword;
}
