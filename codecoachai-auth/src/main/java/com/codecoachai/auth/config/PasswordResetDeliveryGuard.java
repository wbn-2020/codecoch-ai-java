package com.codecoachai.auth.config;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetDeliveryGuard implements ApplicationRunner {

    private final PasswordResetProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        boolean prodProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
        if (properties.mockProvider() && (!properties.isAllowMock() || prodProfile)) {
            throw new IllegalStateException("Mock password reset delivery is not allowed for this environment");
        }
    }
}
