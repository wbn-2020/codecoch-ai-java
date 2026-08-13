package com.codecoachai.auth.domain.dto;

import lombok.Data;

@Data
public class InnerResetPasswordDTO {

    private String passwordHash;
}
