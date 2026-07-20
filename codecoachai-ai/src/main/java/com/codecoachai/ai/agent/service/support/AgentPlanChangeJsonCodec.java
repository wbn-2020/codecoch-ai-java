package com.codecoachai.ai.agent.service.support;

import com.codecoachai.ai.agent.domain.dto.AgentPlanSuggestionIntentDTO;
import com.codecoachai.ai.agent.domain.dto.AgentPlanTaskSnapshotDTO;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AgentPlanChangeJsonCodec {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "自适应计划数据序列化失败。");
        }
    }

    public AgentPlanSuggestionIntentDTO readIntent(String json) {
        if (!StringUtils.hasText(json)) {
            return new AgentPlanSuggestionIntentDTO();
        }
        try {
            return objectMapper.readValue(json, AgentPlanSuggestionIntentDTO.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR, "建议结构无法安全解析，请重新生成复盘。");
        }
    }

    public AgentPlanTaskSnapshotDTO readTaskSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentPlanTaskSnapshotDTO.class);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SEMANTIC_VALIDATION_ERROR, "计划差异快照无法安全解析，请重新预览。");
        }
    }

    public List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public Map<String, Object> readObjectMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public <T> T convert(Object value, Class<T> targetType) {
        try {
            return objectMapper.convertValue(value, targetType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
