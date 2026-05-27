package com.codecoachai.ai.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 多模型路由配置：
 * <pre>
 * codecoachai.ai:
 *   router:
 *     default-provider: deepseek
 *     fallback-provider: dashscope
 *     fallback-enabled: true
 *   providers:
 *     deepseek:
 *       base-url: https://api.deepseek.com
 *       api-key: ${DEEPSEEK_API_KEY:}
 *       chat-model: deepseek-chat
 *       reasoner-model: deepseek-reasoner
 *       timeout-seconds: 60
 *     dashscope:
 *       base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
 *       api-key: ${DASHSCOPE_API_KEY:}
 *       chat-model: qwen-plus
 *       reasoner-model: qwen-max
 *       timeout-seconds: 60
 *   quota:
 *     per-user-day: 200
 *     per-user-minute: 20
 *   retry:
 *     max-attempts: 3
 *     initial-backoff-ms: 500
 *     max-backoff-ms: 5000
 * </pre>
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
        /** 默认主用 provider 名称（与 providers 的 key 对应） */
        private String defaultProvider = "deepseek";
        /** 降级 provider 名称；为空则不启用降级 */
        private String fallbackProvider = "";
        private String embeddingProvider = "dashscope";
        /** 全局是否启用降级 */
        private Boolean fallbackEnabled = true;
    }

    @Data
    public static class ProviderConfig {
        private String baseUrl = "";
        private String apiKey = "";
        /** 通用对话模型 */
        private String chatModel = "";
        /** 推理/长上下文模型（如 deepseek-reasoner / qwen-max） */
        private String reasonerModel = "";
        private String embeddingModel = "";
        private Integer timeoutSeconds = 60;
        private Double temperature = 0.3;
        private Integer maxTokens = 2048;
        /** 单位元 / 1k tokens（用于成本估算） */
        private Double inputPricePerKToken = 0.0;
        private Double outputPricePerKToken = 0.0;

        public Duration timeout() {
            return Duration.ofSeconds(timeoutSeconds == null || timeoutSeconds <= 0 ? 60 : timeoutSeconds);
        }
    }

    @Data
    public static class Quota {
        /** 每用户每日最多次数（null 表示不限）*/
        private Integer perUserDay = 200;
        /** 每用户每分钟最多次数 */
        private Integer perUserMinute = 20;
        /** 是否启用配额检查 */
        private Boolean enabled = true;
    }

    @Data
    public static class Retry {
        private Integer maxAttempts = 3;
        private Long initialBackoffMs = 500L;
        private Long maxBackoffMs = 5000L;
    }
}
