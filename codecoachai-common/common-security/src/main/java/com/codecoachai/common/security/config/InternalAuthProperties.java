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

    private long maxSignedBodyBytes = 1024L * 1024L;

    @PostConstruct
    public void validate() {
        if (enabled && !StringUtils.hasText(secret)) {
            throw new IllegalStateException("codecoachai.internal.auth.secret must be configured");
        }
        if (allowedClockSkewSeconds < 1 || allowedClockSkewSeconds > 900) {
            throw new IllegalStateException(
                    "codecoachai.internal.auth.allowed-clock-skew-seconds must be between 1 and 900");
        }
        if (nonceTtlSeconds < 1 || nonceTtlSeconds > 3600) {
            throw new IllegalStateException(
                    "codecoachai.internal.auth.nonce-ttl-seconds must be between 1 and 3600");
        }
        if (maxSignedBodyBytes < 1 || maxSignedBodyBytes > 16L * 1024L * 1024L) {
            throw new IllegalStateException(
                    "codecoachai.internal.auth.max-signed-body-bytes must be between 1 and 16777216");
        }
    }
}
