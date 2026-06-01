package com.codecoachai.ai.convert;

import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.entity.PromptTemplateVersion;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVersionVO;

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
        vo.setInputVariablesJson(log.getInputVariablesJson());
        vo.setInputVariablesPreview(preview(log.getInputVariablesJson()));
        vo.setModelParamsJson(log.getModelParamsJson());
        vo.setModelParamsPreview(preview(log.getModelParamsJson()));
        vo.setPromptHash(log.getPromptHash());
        vo.setResponseFormat(log.getResponseFormat());
        vo.setRequestPrompt(log.getRequestPrompt());
        vo.setRequestPromptPreview(preview(log.getRequestPrompt()));
        vo.setRequestPreview(vo.getRequestPromptPreview());
        vo.setResponseContent(log.getResponseContent());
        vo.setResponseContentPreview(preview(log.getResponseContent()));
        vo.setResponsePreview(vo.getResponseContentPreview());
        vo.setBusinessId(log.getBusinessId());
        vo.setElapsedMs(log.getElapsedMs());
        vo.setCostMillis(log.getCostMillis());
        vo.setSuccess(log.getSuccess());
        vo.setPromptTokens(log.getPromptTokens());
        vo.setCompletionTokens(log.getCompletionTokens());
        vo.setTotalTokens(log.getTotalTokens());
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setErrorMessagePreview(preview(log.getErrorMessage()));
        vo.setRequestBody(log.getRequestBody());
        vo.setRequestBodyPreview(preview(log.getRequestBody()));
        vo.setResponseBody(log.getResponseBody());
        vo.setResponseBodyPreview(preview(log.getResponseBody()));
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private static String preview(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private static String shortId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);
    }
}
