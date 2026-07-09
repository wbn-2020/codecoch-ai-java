package com.codecoachai.ai.convert;

import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.enums.AiResultSourceEnum;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.security.SensitiveTextMasker;

public final class AiConvert {

    private static final String PROMPT_RAW_ACCESS_PERMISSION = "admin:ai:prompt:raw:view";

    private AiConvert() {
    }

    public static PromptTemplateVO toPromptVO(PromptTemplate template) {
        return toPromptVO(template, false);
    }

    public static PromptTemplateVO toPromptVO(PromptTemplate template, boolean includeRawFields) {
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(template.getId());
        vo.setScene(template.getScene());
        vo.setName(template.getName());
        vo.setTemplateName(template.getTemplateName());
        vo.setDescription(template.getDescription());
        vo.setContent(includeRawFields ? template.getContent() : null);
        vo.setTemplateContent(includeRawFields ? template.getTemplateContent() : null);
        vo.setContentLength(SensitiveTextMasker.length(template.getContent()));
        vo.setContentHash(SensitiveTextMasker.sha256Prefix(template.getContent()));
        vo.setTemplateContentLength(SensitiveTextMasker.length(template.getTemplateContent()));
        vo.setTemplateContentHash(SensitiveTextMasker.sha256Prefix(template.getTemplateContent()));
        vo.setVariables(template.getVariables());
        vo.setVersion(template.getVersion());
        vo.setActiveVersionId(template.getActiveVersionId());
        vo.setEnabled(template.getEnabled());
        vo.setStatus(template.getStatus());
        vo.setRawFieldsAvailable(hasText(template.getContent()) || hasText(template.getTemplateContent()));
        vo.setRawFieldsIncluded(includeRawFields);
        vo.setRawAccessPermission(PROMPT_RAW_ACCESS_PERMISSION);
        return vo;
    }

    public static PromptTemplateVersionVO toVersionVO(PromptTemplateVersion version) {
        return toVersionVO(version, false);
    }

    public static PromptTemplateVersionVO toVersionVO(PromptTemplateVersion version, boolean includeRawFields) {
        PromptTemplateVersionVO vo = new PromptTemplateVersionVO();
        vo.setId(version.getId());
        vo.setTemplateId(version.getTemplateId());
        vo.setScene(version.getScene());
        vo.setVersionCode(version.getVersionCode());
        vo.setVersionName(version.getVersionName());
        vo.setContent(includeRawFields ? version.getContent() : null);
        vo.setContentLength(SensitiveTextMasker.length(version.getContent()));
        vo.setContentHash(SensitiveTextMasker.sha256Prefix(version.getContent()));
        vo.setVariablesJson(version.getVariablesJson());
        vo.setModelParamsJson(includeRawFields ? version.getModelParamsJson() : null);
        vo.setModelParamsLength(SensitiveTextMasker.length(version.getModelParamsJson()));
        vo.setModelParamsHash(SensitiveTextMasker.sha256Prefix(version.getModelParamsJson()));
        vo.setStatus(version.getStatus());
        vo.setIsActive(version.getIsActive());
        vo.setCreatedBy(version.getCreatedBy());
        vo.setActivatedBy(version.getActivatedBy());
        vo.setActivatedAt(version.getActivatedAt());
        vo.setChangeLog(version.getChangeLog());
        vo.setCreatedAt(version.getCreatedAt());
        vo.setUpdatedAt(version.getUpdatedAt());
        vo.setRawFieldsAvailable(hasText(version.getContent()) || hasText(version.getModelParamsJson()));
        vo.setRawFieldsIncluded(includeRawFields);
        vo.setRawAccessPermission(PROMPT_RAW_ACCESS_PERMISSION);
        return vo;
    }

    public static AiCallLogVO toLogVO(AiCallLog log) {
        return toLogVO(log, false);
    }

    public static AiCallLogVO toLogVO(AiCallLog log, boolean includeRawFields) {
        boolean includeTextPreview = includeRawFields || !isHighSensitivityScene(log.getScene());
        AiCallLogVO vo = new AiCallLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setScene(log.getScene());
        vo.setModelName(log.getModelName());
        vo.setPromptTemplateId(log.getPromptTemplateId());
        vo.setPromptTemplateVersionId(log.getPromptTemplateVersionId());
        vo.setPromptVersion(log.getPromptVersion());
        vo.setRequestId(log.getRequestId());
        vo.setShortRequestId(shortId(log.getRequestId()));
        vo.setTraceId(log.getTraceId());
        vo.setTraceIdShort(shortId(log.getTraceId()));
        vo.setShortTraceId(vo.getTraceIdShort());
        vo.setRouteTrace(log.getRouteTrace());
        AiResultSourceEnum resultSource = resolveResultSource(log);
        vo.setResultSource(resultSource.name());
        vo.setResultSourceLabel(resultSource.getLabel());
        vo.setFallback(resultSource == AiResultSourceEnum.FALLBACK);
        vo.setEstimatedCost(log.getEstimatedCost() == null ? log.getTokenCost() : log.getEstimatedCost());
        vo.setInputVariablesJson(includeRawFields ? log.getInputVariablesJson() : null);
        vo.setInputVariablesPreview(includeTextPreview ? preview(log.getInputVariablesJson()) : null);
        vo.setInputVariablesHash(SensitiveTextMasker.sha256Prefix(log.getInputVariablesJson()));
        vo.setModelParamsJson(includeRawFields ? log.getModelParamsJson() : null);
        vo.setModelParamsPreview(includeRawFields ? preview(log.getModelParamsJson()) : null);
        vo.setPromptHash(log.getPromptHash());
        vo.setResponseFormat(log.getResponseFormat());
        vo.setRequestPrompt(includeRawFields ? log.getRequestPrompt() : null);
        vo.setRequestPromptPreview(includeTextPreview ? preview(log.getRequestPrompt()) : null);
        vo.setRequestPromptHash(SensitiveTextMasker.sha256Prefix(log.getRequestPrompt()));
        vo.setRequestPreview(vo.getRequestPromptPreview());
        vo.setResponseContent(includeRawFields ? log.getResponseContent() : null);
        vo.setResponseContentPreview(includeTextPreview ? preview(log.getResponseContent()) : null);
        vo.setResponseContentHash(SensitiveTextMasker.sha256Prefix(log.getResponseContent()));
        vo.setResponsePreview(vo.getResponseContentPreview());
        vo.setBusinessId(log.getBusinessId());
        vo.setElapsedMs(log.getElapsedMs());
        vo.setCostMillis(log.getCostMillis());
        vo.setSuccess(log.getSuccess());
        vo.setPromptTokens(log.getPromptTokens());
        vo.setCompletionTokens(log.getCompletionTokens());
        vo.setTotalTokens(log.getTotalTokens());
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(SensitiveTextMasker.maskText(log.getErrorMessage()));
        vo.setErrorMessagePreview(includeTextPreview ? preview(log.getErrorMessage()) : null);
        vo.setRequestBody(includeRawFields ? log.getRequestBody() : null);
        vo.setRequestBodyPreview(includeTextPreview ? preview(log.getRequestBody()) : null);
        vo.setRequestBodyHash(SensitiveTextMasker.sha256Prefix(log.getRequestBody()));
        vo.setResponseBody(includeRawFields ? log.getResponseBody() : null);
        vo.setResponseBodyPreview(includeTextPreview ? preview(log.getResponseBody()) : null);
        vo.setResponseBodyHash(SensitiveTextMasker.sha256Prefix(log.getResponseBody()));
        vo.setRawFieldsAvailable(hasText(log.getInputVariablesJson()) || hasText(log.getRequestPrompt())
                || hasText(log.getResponseContent()) || hasText(log.getRequestBody()) || hasText(log.getResponseBody()));
        vo.setRawFieldsIncluded(includeRawFields);
        vo.setRawAccessPermission("admin:ai:log:raw:view");
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private static String preview(String value) {
        if (value == null) {
            return null;
        }
        return SensitiveTextMasker.safePreview(value);
    }

    private static boolean isHighSensitivityScene(String scene) {
        if (scene == null) {
            return true;
        }
        String normalized = scene.trim().toUpperCase();
        return normalized.contains("PROMPT")
                || normalized.contains("RESUME")
                || normalized.contains("INTERVIEW")
                || normalized.contains("JOB")
                || normalized.contains("QUESTION")
                || normalized.contains("AGENT")
                || normalized.contains("MATCH");
    }

    private static AiResultSourceEnum resolveResultSource(AiCallLog log) {
        String modelName = lower(log.getModelName());
        String routeTrace = lower(log.getRouteTrace());
        String requestBody = lower(log.getRequestBody());
        String responseBody = lower(log.getResponseBody());
        if (modelName.contains("mock")
                || modelName.contains("模拟")
                || requestBody.contains("\"mockmode\":true")
                || requestBody.contains("mockmode=true")
                || responseBody.contains("\"mockmode\":true")) {
            return AiResultSourceEnum.MOCK;
        }
        if (routeTrace.contains("->")
                || routeTrace.contains("degraded")
                || requestBody.contains("\"fallbackused\":true")
                || requestBody.contains("fallbackused=true")
                || requestBody.contains("degraded")
                || responseBody.contains("\"fallback\":true")
                || responseBody.contains("\"truststatus\":\"fallback\"")
                || responseBody.contains("degraded")) {
            return AiResultSourceEnum.FALLBACK;
        }
        return AiResultSourceEnum.LLM;
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String shortId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
