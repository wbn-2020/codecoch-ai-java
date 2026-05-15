package com.codecoachai.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "codecoachai.internal.auth")
public class InternalAuthProperties {

    private boolean enabled = true;

    private String secret = "";

    private long allowedClockSkewSeconds = 300;

    private long nonceTtlSeconds = 300;
}
