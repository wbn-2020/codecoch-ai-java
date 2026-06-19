package com.codecoachai.ai.service.impl;

import com.codecoachai.ai.client.ProviderAiCaller;
import com.codecoachai.ai.config.AiRouterProperties;
import com.codecoachai.ai.domain.dto.EmbeddingRequestDTO;
import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.vo.EmbeddingResponseVO;
import com.codecoachai.ai.mapper.AiCallLogMapper;
import com.codecoachai.ai.security.AiErrorSanitizer;
import com.codecoachai.ai.service.EmbeddingService;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final int MAX_BATCH_SIZE = 64;
    private static final int MAX_TEXT_LENGTH = 8000;

    private final ProviderAiCaller providerAiCaller;
    private final AiRouterProperties aiRouterProperties;
    private final AiCallLogMapper aiCallLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public EmbeddingResponseVO embed(EmbeddingRequestDTO dto) {
        List<String> texts = normalizeTexts(dto == null ? null : dto.getTexts());
        String provider = resolveProvider(dto == null ? null : dto.getProvider());
        String model = dto == null ? null : dto.getModel();
        long started = System.currentTimeMillis();
        ProviderAiCaller.EmbeddingResult result = null;
        Exception error = null;
        try {
            result = providerAiCaller.embedding(provider, texts, model);
            EmbeddingResponseVO vo = new EmbeddingResponseVO();
            vo.setProvider(result.getProvider());
            vo.setModel(result.getModel());
            vo.setDimension(result.getDimension());
            vo.setVectors(result.getVectors());
            vo.setTotalTokens(result.getTotalTokens());
            vo.setElapsedMs(result.getElapsedMs());
            return vo;
        } catch (Exception ex) {
            error = ex;
            throw ex;
        } finally {
            saveEmbeddingLog(provider, model, texts.size(), result, error, System.currentTimeMillis() - started);
        }
    }

    private List<String> normalizeTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "待处理文本不能为空");
        }
        if (texts.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多处理 64 段文本");
        }
        List<String> normalized = texts.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(text -> text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text)
                .toList();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "待处理文本不能为空");
        }
        return normalized;
    }

    private String resolveProvider(String requestedProvider) {
        if (StringUtils.hasText(requestedProvider)) {
            return requestedProvider;
        }
        String embeddingProvider = aiRouterProperties.getRouter().getEmbeddingProvider();
        if (StringUtils.hasText(embeddingProvider)) {
            return embeddingProvider;
        }
        return aiRouterProperties.getRouter().getDefaultProvider();
    }

    private void saveEmbeddingLog(String provider, String requestedModel, int textCount,
                                  ProviderAiCaller.EmbeddingResult result, Exception error, long elapsedMs) {
        try {
            AiCallLog logEntry = new AiCallLog();
            logEntry.setScene("EMBEDDING");
            logEntry.setRequestId(UUID.randomUUID().toString());
            logEntry.setModelName(result == null ? requestedModel : result.getModel());
            logEntry.setModel(result == null ? requestedModel : result.getModel());
            logEntry.setResponseFormat("VECTOR");
            logEntry.setRequestBody(toJson(Map.of(
                    "provider", provider,
                    "model", StringUtils.hasText(requestedModel) ? requestedModel : "",
                    "textCount", textCount
            )));
            if (result != null) {
                logEntry.setTotalTokens(result.getTotalTokens());
                logEntry.setPromptTokens(result.getPromptTokens());
                logEntry.setRouteTrace(result.getProvider());
                logEntry.setResponseBody(toJson(Map.of(
                        "dimension", result.getDimension(),
                        "vectorCount", result.getVectors().size()
                )));
                logEntry.setSuccess(1);
                logEntry.setStatus(1);
            } else {
                logEntry.setSuccess(0);
                logEntry.setStatus(0);
            }
            if (error != null) {
                logEntry.setErrorMessage(AiErrorSanitizer.safeFailureSummary(error));
            }
            logEntry.setElapsedMs(elapsedMs);
            logEntry.setCostMillis(elapsedMs);
            aiCallLogMapper.insert(logEntry);
        } catch (Exception ex) {
            log.warn("Embedding call log write failed provider={}", provider, ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
