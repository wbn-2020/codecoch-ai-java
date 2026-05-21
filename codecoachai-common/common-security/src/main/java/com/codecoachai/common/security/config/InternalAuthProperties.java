package com.codecoachai.common.security.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Data
@ConfigurationProperties(prefix = "codecoachai.internal.auth")
public class InternalAuthProperties {

    private boolean enabled = true;

    private String secret = "";

    private long allowedClockSkewSeconds = 300;

    private long nonceTtlSeconds = 300;

    @PostConstruct
    public void validate() {
        if (enabled && !StringUtils.hasText(secret)) {
            throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
        }
    }
}
