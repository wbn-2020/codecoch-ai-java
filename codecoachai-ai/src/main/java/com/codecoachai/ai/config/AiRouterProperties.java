package com.codecoachai.ai.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI router configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.ai")
public class AiRouterProperties {

    private Router router = new Router();

    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    private Quota quota = new Quota();

    private Retry retry = new Retry();

    @Data
    public static class Router {
        /** Default primary provider name. */
        private String defaultProvider = "deepseek";
        /** Fallback provider name. Empty means disabled. */
        private String fallbackProvider = "";
        private String embeddingProvider = "dashscope";
        /** Whether fallback routing is enabled globally. */
        private Boolean fallbackEnabled = true;
    }

    @Data
    public static class ProviderConfig {
        private String baseUrl = "";
        private String apiKey = "";
        /** General chat model. */
        private String chatModel = "";
        /** Reasoning model, such as deepseek-reasoner or qwen-max. */
        private String reasonerModel = "";
        private String embeddingModel = "";
        private Integer timeoutSeconds = 60;
        private Double temperature = 0.3;
        private Integer maxTokens = 2048;
        /** Price per 1k input tokens. */
        private Double inputPricePerKToken = 0.0;
        private Double outputPricePerKToken = 0.0;

        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds == null || timeoutSeconds <= 0 ? 60 : timeoutSeconds);
        }
    }

    @Data
    public static class Quota {
        /** Daily limit per user. Null means unlimited. */
        private Integer perUserDay = 200;
        /** Per-minute limit per user. */
        private Integer perUserMinute = 20;
        /** Whether quota checks are enabled. */
        private Boolean enabled = true;
    }

    @Data
    public static class Retry {
        private Integer maxAttempts = 3;
        private Long initialBackoffMs = 500L;
        private Long maxBackoffMs = 5000L;
    }
}
