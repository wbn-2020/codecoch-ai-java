package com.codecoachai.ai.convert;

import com.codecoachai.ai.domain.entity.AiCallLog;
import com.codecoachai.ai.domain.entity.PromptTemplate;
import com.codecoachai.ai.domain.vo.AiCallLogVO;
import com.codecoachai.ai.domain.vo.PromptTemplateVO;

public final class AiConvert {

    private AiConvert() {
    }

    public static PromptTemplateVO toPromptVO(PromptTemplate template) {
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(template.getId());
        vo.setScene(template.getScene());
        vo.setName(template.getName());
        vo.setTemplateName(template.getTemplateName());
        vo.setContent(template.getContent());
        vo.setTemplateContent(template.getTemplateContent());
        vo.setVariables(template.getVariables());
        vo.setVersion(template.getVersion());
        vo.setStatus(template.getStatus());
        return vo;
    }

    public static AiCallLogVO toLogVO(AiCallLog log) {
        AiCallLogVO vo = new AiCallLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setScene(log.getScene());
        vo.setModelName(log.getModelName());
        vo.setPromptTemplateId(log.getPromptTemplateId());
        vo.setRequestPrompt(log.getRequestPrompt());
        vo.setResponseContent(log.getResponseContent());
        vo.setBusinessId(log.getBusinessId());
        vo.setElapsedMs(log.getElapsedMs());
        vo.setCostMillis(log.getCostMillis());
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setRequestBody(log.getRequestBody());
        vo.setResponseBody(log.getResponseBody());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
