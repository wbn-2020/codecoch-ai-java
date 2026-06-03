package com.codecoachai.ai.convert;

import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;
import com.codecoachai.ai.security.SensitiveTextMasker;

public final class AiConvert {

    private AiConvert() {
    }

    public static PromptTemplateVO toPromptVO(PromptTemplate template) {
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(template.getId());
        vo.setScene(template.getScene());
        vo.setName(template.getName());
        vo.setTemplateName(template.getTemplateName());
        vo.setDescription(template.getDescription());
        vo.setContent(template.getContent());
        vo.setTemplateContent(template.getTemplateContent());
        vo.setVariables(template.getVariables());
        vo.setVersion(template.getVersion());
        vo.setActiveVersionId(template.getActiveVersionId());
        vo.setEnabled(template.getEnabled());
        vo.setStatus(template.getStatus());
        return vo;
    }

    public static PromptTemplateVersionVO toVersionVO(PromptTemplateVersion version) {
        PromptTemplateVersionVO vo = new PromptTemplateVersionVO();
        vo.setId(version.getId());
        vo.setTemplateId(version.getTemplateId());
        vo.setScene(version.getScene());
        vo.setVersionCode(version.getVersionCode());
        vo.setVersionName(version.getVersionName());
        vo.setContent(version.getContent());
        vo.setVariablesJson(version.getVariablesJson());
        vo.setModelParamsJson(version.getModelParamsJson());
        vo.setStatus(version.getStatus());
        vo.setIsActive(version.getIsActive());
        vo.setCreatedBy(version.getCreatedBy());
        vo.setActivatedBy(version.getActivatedBy());
        vo.setActivatedAt(version.getActivatedAt());
        vo.setChangeLog(version.getChangeLog());
        vo.setCreatedAt(version.getCreatedAt());
        vo.setUpdatedAt(version.getUpdatedAt());
        return vo;
    }

    public static AiCallLogVO toLogVO(AiCallLog log) {
        return toLogVO(log, false);
    }

    public static AiCallLogVO toLogVO(AiCallLog log, boolean includeRawFields) {
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
        vo.setEstimatedCost(log.getEstimatedCost() == null ? log.getTokenCost() : log.getEstimatedCost());
        vo.setInputVariablesJson(includeRawFields ? log.getInputVariablesJson() : null);
        vo.setInputVariablesPreview(preview(log.getInputVariablesJson()));
        vo.setInputVariablesHash(SensitiveTextMasker.sha256Prefix(log.getInputVariablesJson()));
        vo.setModelParamsJson(log.getModelParamsJson());
        vo.setModelParamsPreview(preview(log.getModelParamsJson()));
        vo.setPromptHash(log.getPromptHash());
        vo.setResponseFormat(log.getResponseFormat());
        vo.setRequestPrompt(includeRawFields ? log.getRequestPrompt() : null);
        vo.setRequestPromptPreview(preview(log.getRequestPrompt()));
        vo.setRequestPromptHash(SensitiveTextMasker.sha256Prefix(log.getRequestPrompt()));
        vo.setRequestPreview(vo.getRequestPromptPreview());
        vo.setResponseContent(includeRawFields ? log.getResponseContent() : null);
        vo.setResponseContentPreview(preview(log.getResponseContent()));
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
        vo.setErrorMessagePreview(preview(log.getErrorMessage()));
        vo.setRequestBody(includeRawFields ? log.getRequestBody() : null);
        vo.setRequestBodyPreview(preview(log.getRequestBody()));
        vo.setRequestBodyHash(SensitiveTextMasker.sha256Prefix(log.getRequestBody()));
        vo.setResponseBody(includeRawFields ? log.getResponseBody() : null);
        vo.setResponseBodyPreview(preview(log.getResponseBody()));
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
