package com.codecoachai.auth.domain.vo;

import lombok.Data;

@Data
public class ForgotPasswordVO {

    private String message;
    /** TTL for the out-of-band credential; plaintext credential is never returned. */
    private Long expiresInSeconds;
}
