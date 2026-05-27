package com.codecoachai.ai.client;

import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 可指定 provider 的 OpenAI 兼容协议调用器。
 * <p>与现有 {@link OpenAiCompatibleClient} 区别：本类不绑定单一 provider，调用时按 provider 名字获取配置。
 * 用于 {@code AiModelRouter} 的多 provider + 降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderAiCaller {

    private final AiRouterProperties routerProperties;
    private final AiModelConfigMapper modelConfigMapper;
    private final AesGcmTextEncryptor apiKeyEncryptor;
    private final ObjectMapper objectMapper;

    /**
     * 按 provider 名调用 chat 接口。
     *
     * @param providerName provider 名（与 codecoachai.ai.providers 下的 key 对应）
     * @param prompt       完整 prompt
     * @param modelType    chat / reasoner（决定用 chatModel 还是 reasonerModel）
     */
    public CallResult chat(String providerName, String prompt, String modelType) {
        AiRouterProperties.ProviderConfig cfg = resolveProvider(providerName);
        if (cfg == null) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider not configured: " + providerName);
        }
        if (!StringUtils.hasText(cfg.getBaseUrl()) || !StringUtils.hasText(cfg.getApiKey())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider base-url or api-key empty: " + providerName);
        }

        String model = "reasoner".equalsIgnoreCase(modelType)
                ? (StringUtils.hasText(cfg.getReasonerModel()) ? cfg.getReasonerModel() : cfg.getChatModel())
                : cfg.getChatModel();

        String url = normalizeUrl(cfg.getBaseUrl());
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", cfg.getTemperature(),
                "max_tokens", cfg.getMaxTokens(),
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        long started = System.currentTimeMillis();
        try {
            String response = RestClient.builder()
                    .requestFactory(requestFactory(cfg.timeout()))
                    .build()
                    .post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode choice = root.path("choices").path(0).path("message").path("content");
            if (!choice.isTextual() || !StringUtils.hasText(choice.asText())) {
                throw new AiProviderException(AiFailureType.EMPTY_RESPONSE,
                        "Provider " + providerName + " empty response");
            }

            CallResult result = new CallResult();
            result.setProvider(providerName);
            result.setModel(model);
            result.setContent(choice.asText());
            result.setPromptTokens(intOrZero(root.path("usage").path("prompt_tokens")));
            result.setCompletionTokens(intOrZero(root.path("usage").path("completion_tokens")));
            result.setTotalTokens(intOrZero(root.path("usage").path("total_tokens")));
            result.setElapsedMs(System.currentTimeMillis() - started);
            result.setEstimatedCost(estimateCost(cfg, result.getPromptTokens(), result.getCompletionTokens()));
            return result;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            AiFailureType type = code == 429 ? AiFailureType.HTTP_ERROR : AiFailureType.HTTP_ERROR;
            throw new AiProviderException(type,
                    "Provider " + providerName + " HTTP " + code, code, ex);
        } catch (ResourceAccessException ex) {
            AiFailureType type = containsCause(ex, SocketTimeoutException.class)
                    || containsCause(ex, ConnectException.class)
                    ? AiFailureType.TIMEOUT
                    : AiFailureType.UNKNOWN_ERROR;
            throw new AiProviderException(type,
                    "Provider " + providerName + " failed: " + ex.getMessage(), null, ex);
        } catch (Exception ex) {
            throw new AiProviderException(AiFailureType.UNKNOWN_ERROR,
                    "Provider " + providerName + " failed: " + ex.getMessage(), null, ex);
        }
    }

    public EmbeddingResult embedding(String providerName, List<String> inputs, String overrideModel) {
        AiRouterProperties.ProviderConfig cfg = resolveProvider(providerName);
        if (cfg == null) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider not configured: " + providerName);
        }
        if (!StringUtils.hasText(cfg.getBaseUrl()) || !StringUtils.hasText(cfg.getApiKey())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider base-url or api-key empty: " + providerName);
        }
        String model = StringUtils.hasText(overrideModel) ? overrideModel : cfg.getEmbeddingModel();
        if (!StringUtils.hasText(model)) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider embedding model empty: " + providerName);
        }

        Map<String, Object> body = Map.of("model", model, "input", inputs);
        long started = System.currentTimeMillis();
        try {
            String response = RestClient.builder()
                    .requestFactory(requestFactory(cfg.timeout()))
                    .build()
                    .post()
                    .uri(normalizeEmbeddingUrl(cfg.getBaseUrl()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            List<List<Float>> vectors = new java.util.ArrayList<>();
            for (JsonNode item : root.path("data")) {
                List<Float> vector = new java.util.ArrayList<>();
                for (JsonNode value : item.path("embedding")) {
                    vector.add((float) value.asDouble());
                }
                vectors.add(vector);
            }
            if (vectors.isEmpty()) {
                throw new AiProviderException(AiFailureType.EMPTY_RESPONSE,
                        "Provider " + providerName + " empty embedding response");
            }
            EmbeddingResult result = new EmbeddingResult();
            result.setProvider(providerName);
            result.setModel(model);
            result.setVectors(vectors);
            result.setDimension(vectors.get(0).size());
            result.setPromptTokens(intOrZero(root.path("usage").path("prompt_tokens")));
            result.setTotalTokens(intOrZero(root.path("usage").path("total_tokens")));
            result.setElapsedMs(System.currentTimeMillis() - started);
            return result;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(AiFailureType.HTTP_ERROR,
                    "Provider " + providerName + " embedding HTTP " + ex.getStatusCode().value(),
                    ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            AiFailureType type = containsCause(ex, SocketTimeoutException.class)
                    || containsCause(ex, ConnectException.class)
                    ? AiFailureType.TIMEOUT
                    : AiFailureType.UNKNOWN_ERROR;
            throw new AiProviderException(type,
                    "Provider " + providerName + " embedding failed: " + ex.getMessage(), null, ex);
        } catch (Exception ex) {
            throw new AiProviderException(AiFailureType.UNKNOWN_ERROR,
                    "Provider " + providerName + " embedding failed: " + ex.getMessage(), null, ex);
        }
    }

    private AiRouterProperties.ProviderConfig resolveProvider(String providerName) {
        AiRouterProperties.ProviderConfig configured = routerProperties.getProviders().get(providerName);
        if (isUsable(configured)) {
            return configured;
        }
        AiRouterProperties.ProviderConfig fromDatabase = loadProviderFromDatabase(providerName);
        return fromDatabase != null ? fromDatabase : configured;
    }

    private boolean isUsable(AiRouterProperties.ProviderConfig cfg) {
        return cfg != null && StringUtils.hasText(cfg.getBaseUrl()) && StringUtils.hasText(cfg.getApiKey());
    }

    private AiRouterProperties.ProviderConfig loadProviderFromDatabase(String providerName) {
        if (!StringUtils.hasText(providerName)) {
            return null;
        }
        AiModelConfig model = modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getProvider, providerName)
                .eq(AiModelConfig::getEnabled, 1)
                .orderByDesc(AiModelConfig::getDefaultModel)
                .orderByAsc(AiModelConfig::getSortOrder)
                .orderByDesc(AiModelConfig::getUpdatedAt)
                .last("LIMIT 1"));
        if (model == null) {
            return null;
        }
        AiRouterProperties.ProviderConfig cfg = new AiRouterProperties.ProviderConfig();
        cfg.setBaseUrl(model.getApiBaseUrl());
        cfg.setApiKey(decryptApiKey(providerName, model.getApiKey()));
        cfg.setChatModel(model.getModelCode());
        cfg.setReasonerModel(model.getModelCode());
        cfg.setEmbeddingModel(model.getModelCode());
        cfg.setTemperature(model.getTemperature() == null ? 0.3 : model.getTemperature());
        cfg.setMaxTokens(model.getMaxTokens() == null ? 2048 : model.getMaxTokens());
        return cfg;
    }

    private String decryptApiKey(String providerName, String storedApiKey) {
        try {
            return apiKeyEncryptor.decryptIfNeeded(storedApiKey);
        } catch (IllegalStateException ex) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider api-key decrypt failed: " + providerName, null, ex);
        }
    }

    private String normalizeUrl(String baseUrl) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return b.endsWith("/chat/completions") ? b : b + "/chat/completions";
    }

    private String normalizeEmbeddingUrl(String baseUrl) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (b.endsWith("/chat/completions")) {
            b = b.substring(0, b.length() - "/chat/completions".length());
        }
        return b.endsWith("/embeddings") ? b : b + "/embeddings";
    }

    private int intOrZero(JsonNode node) {
        return node.isNumber() ? node.asInt() : 0;
    }

    private Double estimateCost(AiRouterProperties.ProviderConfig cfg, int inTokens, int outTokens) {
        double in = cfg.getInputPricePerKToken() == null ? 0.0 : cfg.getInputPricePerKToken();
        double out = cfg.getOutputPricePerKToken() == null ? 0.0 : cfg.getOutputPricePerKToken();
        return (inTokens / 1000.0) * in + (outTokens / 1000.0) * out;
    }

    private boolean containsCause(Throwable throwable, Class<? extends Throwable> targetType) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (targetType.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private org.springframework.http.client.ClientHttpRequestFactory requestFactory(Duration timeout) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    /** chat 结果（含计费信息） */
    @Data
    public static class CallResult {
        private String provider;
        private String model;
        private String content;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Long elapsedMs;
        private Double estimatedCost;
    }

    @Data
    public static class EmbeddingResult {
        private String provider;
        private String model;
        private List<List<Float>> vectors;
        private Integer dimension;
        private Integer promptTokens;
        private Integer totalTokens;
        private Long elapsedMs;
    }
}
