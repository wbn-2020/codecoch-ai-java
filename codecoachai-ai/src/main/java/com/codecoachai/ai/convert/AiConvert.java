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
        vo.setContent(template.getContent());
        vo.setStatus(template.getStatus());
        return vo;
    }

    public static AiCallLogVO toLogVO(AiCallLog log) {
        AiCallLogVO vo = new AiCallLogVO();
        vo.setId(log.getId());
        vo.setScene(log.getScene());
        vo.setCostMillis(log.getCostMillis());
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setRequestBody(log.getRequestBody());
        vo.setResponseBody(log.getResponseBody());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
