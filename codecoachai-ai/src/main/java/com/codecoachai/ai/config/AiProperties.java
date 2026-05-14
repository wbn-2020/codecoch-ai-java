package com.codecoachai.ai.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.ai")
public class AiProperties {

    private Boolean enabled = true;
    private Boolean mockEnabled = false;
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
}
