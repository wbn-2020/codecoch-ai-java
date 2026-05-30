package com.codecoachai.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "codecoachai.auth.password-reset")
public class PasswordResetProperties {

    private String deliveryProvider = "mock";

    private boolean allowMock = true;

    private String resetUrlTemplate = "http://localhost:5173/reset-password?token={token}";

    private String from = "";

    private String subject = "CodeCoachAI password reset";

    public boolean mockProvider() {
        return "mock".equalsIgnoreCase(deliveryProvider);
    }
}
