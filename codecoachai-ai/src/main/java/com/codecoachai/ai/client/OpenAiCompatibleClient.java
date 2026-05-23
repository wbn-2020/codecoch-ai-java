package com.codecoachai.ai.client;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.ai.domain.enums.AiFailureType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient implements AiClient {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String chat(String prompt) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new AiProviderException(AiFailureType.CONFIG_ERROR, "AI service is disabled");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getModel())) {
            // 第三方 AI 调用必须显式配置，避免验收时误把未配置环境当成模型返回失败。
            throw new AiProviderException(AiFailureType.CONFIG_ERROR, "AI base-url, api-key or model is not configured");
        }

        String baseUrl = properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
        String url = baseUrl.endsWith("/chat/completions") ? baseUrl : baseUrl + "/chat/completions";
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "temperature", properties.getTemperature(),
                "max_tokens", properties.getMaxTokens(),
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
        try {
            String response = RestClient.builder()
                    .requestFactory(requestFactory(properties.timeout()))
                    .build()
                    .post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            // OpenAI 兼容协议的成功 HTTP 响应也可能没有可用内容，需单独归类为空响应。
            if (!content.isTextual() || !StringUtils.hasText(content.asText())) {
                throw new AiProviderException(AiFailureType.EMPTY_RESPONSE, "AI response content is empty");
            }
            return content.asText();
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new AiProviderException(AiFailureType.HTTP_ERROR,
                    "AI request returned HTTP " + ex.getStatusCode().value(),
                    ex.getStatusCode().value(),
                    ex);
        } catch (ResourceAccessException ex) {
            // 连接超时和读取超时归为 TIMEOUT，便于上层熔断、重试或降级时做精确判断。
            AiFailureType type = containsCause(ex, SocketTimeoutException.class)
                    || containsCause(ex, ConnectException.class)
                    ? AiFailureType.TIMEOUT
                    : AiFailureType.UNKNOWN_ERROR;
            throw new AiProviderException(type, "AI request failed: " + ex.getMessage(), null, ex);
        } catch (Exception ex) {
            throw new AiProviderException(AiFailureType.UNKNOWN_ERROR, "AI request failed: " + ex.getMessage(), null, ex);
        }
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
}
