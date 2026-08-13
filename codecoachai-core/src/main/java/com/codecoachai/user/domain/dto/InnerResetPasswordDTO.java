package com.codecoachai.user.domain.dto;

import lombok.Data;

@Data
public class InnerResetPasswordDTO {

    private String passwordHash;
}
