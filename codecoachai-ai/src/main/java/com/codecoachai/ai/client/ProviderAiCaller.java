package com.codecoachai.ai.client;

import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.entity.AiModelConfig;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.codecoachai.ai.mapper.AiModelConfigMapper;
import com.codecoachai.ai.security.AesGcmTextEncryptor;
import com.codecoachai.ai.security.AiProviderEndpointPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private final AiProviderEndpointPolicy endpointPolicy;
    private final SecureProviderHttpClient providerHttpClient;

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
        return chatWithConfig(providerName, cfg, prompt, modelType);
    }

    /**
     * 对指定的数据库模型配置执行一次低成本实时测活。
     * <p>测活不依赖 enabled 状态，但仍复用端点白名单、TLS 和安全 HTTP 客户端。
     */
    public CallResult probe(AiModelConfig modelConfig, String prompt) {
        return chat(modelConfig, prompt, "chat", true);
    }

    /**
     * 使用一条精确的数据库模型配置调用模型，不再按 provider 二次选取模型。
     * 供全局默认模型路由和管理端测活共用。
     */
    public CallResult chat(AiModelConfig modelConfig, String prompt, String modelType) {
        return chat(modelConfig, prompt, modelType, false);
    }

    private CallResult chat(
            AiModelConfig modelConfig,
            String prompt,
            String modelType,
            boolean lowCostProbe) {
        if (modelConfig == null
                || !StringUtils.hasText(modelConfig.getProvider())
                || !StringUtils.hasText(modelConfig.getModelCode())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "AI model config is incomplete");
        }
        AiRouterProperties.ProviderConfig cfg = databaseProviderConfig(modelConfig);
        if (!StringUtils.hasText(cfg.getBaseUrl()) || !StringUtils.hasText(cfg.getApiKey())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider base-url or api-key empty: " + modelConfig.getProvider());
        }
        cfg.setChatModel(modelConfig.getModelCode());
        cfg.setReasonerModel(modelConfig.getModelCode());
        if (lowCostProbe) {
            cfg.setTemperature(0.0);
            cfg.setMaxTokens(Math.min(
                    cfg.getMaxTokens() == null || cfg.getMaxTokens() <= 0 ? 64 : cfg.getMaxTokens(),
                    64));
        }
        return chatWithConfig(modelConfig.getProvider(), cfg, prompt, modelType);
    }

    private CallResult chatWithConfig(
            String providerName,
            AiRouterProperties.ProviderConfig cfg,
            String prompt,
            String modelType) {

        String model = "reasoner".equalsIgnoreCase(modelType)
                ? (StringUtils.hasText(cfg.getReasonerModel()) ? cfg.getReasonerModel() : cfg.getChatModel())
                : cfg.getChatModel();
        if (!StringUtils.hasText(model)) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider chat model empty: " + providerName);
        }

        URI url = resolveChatEndpoint(providerName, cfg.getBaseUrl());
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", cfg.getTemperature(),
                "max_tokens", cfg.getMaxTokens(),
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        long started = System.currentTimeMillis();
        try {
            String response = providerHttpClient.postJson(
                    url, cfg.getApiKey(), objectMapper.writeValueAsString(body), cfg.timeout());
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
        } catch (SecureProviderHttpClient.ProviderHttpStatusException ex) {
            throw new AiProviderException(AiFailureType.HTTP_ERROR,
                    "Provider " + providerName + " HTTP " + ex.getStatusCode(), ex.getStatusCode(), ex);
        } catch (Exception ex) {
            AiFailureType type = isTimeout(ex) ? AiFailureType.TIMEOUT : AiFailureType.UNKNOWN_ERROR;
            throw new AiProviderException(type,
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
        URI url = resolveEmbeddingEndpoint(providerName, cfg.getBaseUrl());
        long started = System.currentTimeMillis();
        try {
            String response = providerHttpClient.postJson(
                    url, cfg.getApiKey(), objectMapper.writeValueAsString(body), cfg.timeout());
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
        } catch (SecureProviderHttpClient.ProviderHttpStatusException ex) {
            throw new AiProviderException(AiFailureType.HTTP_ERROR,
                    "Provider " + providerName + " embedding HTTP " + ex.getStatusCode(),
                    ex.getStatusCode(), ex);
        } catch (Exception ex) {
            AiFailureType type = isTimeout(ex) ? AiFailureType.TIMEOUT : AiFailureType.UNKNOWN_ERROR;
            throw new AiProviderException(type,
                    "Provider " + providerName + " embedding failed: " + ex.getMessage(), null, ex);
        }
    }

    /**
     * 按 provider 名以流式（SSE）方式调用 chat 接口。逐 token 回调 onDelta，并返回汇总结果。
     * 走 OpenAI 兼容的 stream=true 协议，解析 data: 行。失败抛 {@link AiProviderException}。
     *
     * @param onDelta 每个增量 token 片段的回调（非空、非 [DONE]）
     */
    public CallResult chatStream(String providerName, String prompt, String modelType, Consumer<String> onDelta) {
        AiRouterProperties.ProviderConfig cfg = resolveProvider(providerName);
        if (cfg == null) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider not configured: " + providerName);
        }
        if (!StringUtils.hasText(cfg.getBaseUrl()) || !StringUtils.hasText(cfg.getApiKey())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider base-url or api-key empty: " + providerName);
        }
        return chatStreamWithConfig(providerName, cfg, prompt, modelType, onDelta);
    }

    /**
     * 使用一条精确的数据库模型配置执行流式调用。
     */
    public CallResult chatStream(
            AiModelConfig modelConfig,
            String prompt,
            String modelType,
            Consumer<String> onDelta) {
        if (modelConfig == null
                || !StringUtils.hasText(modelConfig.getProvider())
                || !StringUtils.hasText(modelConfig.getModelCode())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "AI model config is incomplete");
        }
        AiRouterProperties.ProviderConfig cfg = databaseProviderConfig(modelConfig);
        if (!StringUtils.hasText(cfg.getBaseUrl()) || !StringUtils.hasText(cfg.getApiKey())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider base-url or api-key empty: " + modelConfig.getProvider());
        }
        return chatStreamWithConfig(modelConfig.getProvider(), cfg, prompt, modelType, onDelta);
    }

    private CallResult chatStreamWithConfig(
            String providerName,
            AiRouterProperties.ProviderConfig cfg,
            String prompt,
            String modelType,
            Consumer<String> onDelta) {
        String model = "reasoner".equalsIgnoreCase(modelType)
                ? (StringUtils.hasText(cfg.getReasonerModel()) ? cfg.getReasonerModel() : cfg.getChatModel())
                : cfg.getChatModel();
        if (!StringUtils.hasText(model)) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider chat model empty: " + providerName);
        }
        URI url = resolveChatEndpoint(providerName, cfg.getBaseUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", cfg.getTemperature());
        body.put("max_tokens", cfg.getMaxTokens());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        long started = System.currentTimeMillis();
        StringBuilder fullContent = new StringBuilder();
        int[] usage = new int[]{0, 0, 0}; // prompt, completion, total
        try {
            providerHttpClient.postJsonLines(
                    url,
                    cfg.getApiKey(),
                    objectMapper.writeValueAsString(body),
                    cfg.timeout(),
                    line -> {
                        if (line == null || line.isBlank() || !line.startsWith("data:")) {
                            return;
                        }
                        String payload = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(payload)) {
                            return;
                        }
                        try {
                            JsonNode node = objectMapper.readTree(payload);
                            JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                            if (delta.isTextual() && !delta.asText().isEmpty()) {
                                String piece = delta.asText();
                                fullContent.append(piece);
                                if (onDelta != null) {
                                    onDelta.accept(piece);
                                }
                            }
                            JsonNode usageNode = node.path("usage");
                            if (usageNode.isObject()) {
                                usage[0] = intOrZero(usageNode.path("prompt_tokens"));
                                usage[1] = intOrZero(usageNode.path("completion_tokens"));
                                usage[2] = intOrZero(usageNode.path("total_tokens"));
                            }
                        } catch (Exception ex) {
                            throw new AiProviderException(AiFailureType.UNKNOWN_ERROR,
                                    "Provider " + providerName + " returned an invalid stream frame", null, ex);
                        }
                    });
            if (fullContent.length() == 0) {
                throw new AiProviderException(AiFailureType.EMPTY_RESPONSE,
                        "Provider " + providerName + " empty stream response");
            }
            CallResult result = new CallResult();
            result.setProvider(providerName);
            result.setModel(model);
            result.setContent(fullContent.toString());
            result.setPromptTokens(usage[0]);
            result.setCompletionTokens(usage[1]);
            result.setTotalTokens(usage[2]);
            result.setElapsedMs(System.currentTimeMillis() - started);
            result.setEstimatedCost(estimateCost(cfg, usage[0], usage[1]));
            return result;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (SecureProviderHttpClient.ProviderHttpStatusException ex) {
            throw new AiProviderException(AiFailureType.HTTP_ERROR,
                    "Provider " + providerName + " stream HTTP " + ex.getStatusCode(),
                    ex.getStatusCode(), ex);
        } catch (Exception ex) {
            AiFailureType type = isTimeout(ex) ? AiFailureType.TIMEOUT : AiFailureType.UNKNOWN_ERROR;
            throw new AiProviderException(type,
                    "Provider " + providerName + " stream failed: " + ex.getMessage(), null, ex);
        }
    }

    private AiRouterProperties.ProviderConfig resolveProvider(String providerName) {
        AiRouterProperties.ProviderConfig fromDatabase = loadProviderFromDatabase(providerName);
        if (isUsable(fromDatabase)) {
            return fromDatabase;
        }
        return routerProperties.getProviders().get(providerName);
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
        return databaseProviderConfig(model);
    }

    private AiRouterProperties.ProviderConfig databaseProviderConfig(AiModelConfig model) {
        AiRouterProperties.ProviderConfig cfg = new AiRouterProperties.ProviderConfig();
        cfg.setBaseUrl(model.getApiBaseUrl());
        cfg.setApiKey(decryptApiKey(model.getProvider(), model.getApiKey()));
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

    private URI resolveChatEndpoint(String providerName, String baseUrl) {
        try {
            return endpointPolicy.chatEndpoint(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider endpoint rejected: " + providerName, null, ex);
        }
    }

    /**
     * 返回唯一的启用全局默认模型；配置库不可用、零默认或多默认时返回 {@code null}，
     * 由业务路由层阻止调用，避免运行配置静默覆盖管理端选择。
     */
    public AiModelConfig findUniqueEnabledGlobalDefaultModel() {
        List<AiModelConfig> candidates;
        try {
            candidates = modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getEnabled, 1)
                    .eq(AiModelConfig::getDefaultModel, 1)
                    .orderByDesc(AiModelConfig::getUpdatedAt)
                    .orderByAsc(AiModelConfig::getSortOrder)
                    .last("LIMIT 2"));
        } catch (RuntimeException ex) {
            log.warn("Unable to resolve unique database global default AI model; business routing will be blocked", ex);
            return null;
        }
        if (candidates == null || candidates.size() != 1) {
            return null;
        }
        return candidates.get(0);
    }

    private URI resolveEmbeddingEndpoint(String providerName, String baseUrl) {
        try {
            return endpointPolicy.embeddingEndpoint(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR,
                    "Provider endpoint rejected: " + providerName, null, ex);
        }
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

    private boolean isTimeout(Throwable throwable) {
        return containsCause(throwable, SocketTimeoutException.class)
                || containsCause(throwable, ConnectException.class)
                || containsCause(throwable, ConnectTimeoutException.class);
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
