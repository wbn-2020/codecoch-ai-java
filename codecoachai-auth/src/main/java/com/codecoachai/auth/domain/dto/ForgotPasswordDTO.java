package com.codecoachai.auth.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordDTO {

    @NotBlank(message = "email is required")
    @Email(message = "email format is invalid")
    private String email;
}
