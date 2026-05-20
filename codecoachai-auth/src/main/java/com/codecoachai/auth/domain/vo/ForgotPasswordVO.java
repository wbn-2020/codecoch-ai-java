package com.codecoachai.auth.domain.vo;

import lombok.Data;

@Data
public class ForgotPasswordVO {

    private String message;
    private String resetToken;
    private Long expiresInSeconds;
}
