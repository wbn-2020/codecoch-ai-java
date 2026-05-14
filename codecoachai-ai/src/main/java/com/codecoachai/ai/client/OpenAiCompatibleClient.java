package com.codecoachai.ai.client;

import com.codecoachai.ai.config.AiProperties;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient implements AiClient {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String chat(String prompt) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI service is disabled");
        }
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(properties.getApiKey())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI base-url or api-key is not configured");
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
            if (!content.isTextual()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI response content is empty");
            }
            return content.asText();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI request failed: " + ex.getMessage());
        }
    }

    private org.springframework.http.client.ClientHttpRequestFactory requestFactory(Duration timeout) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
