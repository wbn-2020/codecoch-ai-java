package com.codecoachai.ai.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.ai")
public class AiProperties {

    public static final String MOCK_ENABLED_PROPERTY = "codecoachai.ai.mock-enabled";

    private Boolean enabled = true;
    /**
     * Runtime-only switch bound from Nacos or another Spring property source.
     *
     * <p>The legacy {@code system_config.ai.mock.enabled} database record is
     * intentionally not read by the AI runtime.</p>
     */
    private Boolean mockEnabled;
    private String provider = "openai-compatible";
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private Double temperature = 0.3;
    private Integer maxTokens = 2048;
    private Integer timeoutSeconds = 30;

    public Duration timeout() {
        return Duration.ofSeconds(timeoutSeconds == null || timeoutSeconds <= 0 ? 30 : timeoutSeconds);
    }

    public boolean isMockModeConfigured() {
        return mockEnabled != null;
    }

    public boolean isMockModeEnabled() {
        return Boolean.TRUE.equals(mockEnabled);
    }

    @PostConstruct
    public void validate() {
        if (Boolean.FALSE.equals(enabled)) {
            return;
        }
        if (!isMockModeConfigured()) {
            throw new IllegalStateException(
                    MOCK_ENABLED_PROPERTY
                            + " must be explicitly configured by Nacos or another Spring runtime property source");
        }
        if (isMockModeEnabled()) {
            return;
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("codecoachai.ai.base-url must be configured when real AI calls are enabled");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("codecoachai.ai.api-key must be configured when real AI calls are enabled");
        }
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("codecoachai.ai.model must be configured when real AI calls are enabled");
        }
    }
}
