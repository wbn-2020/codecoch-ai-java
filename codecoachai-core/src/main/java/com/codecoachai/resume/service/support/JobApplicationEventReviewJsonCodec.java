package com.codecoachai.resume.service.support;

import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.resume.domain.vo.JobApplicationEventStructuredReviewVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JobApplicationEventReviewJsonCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public Map<String, Object> readRoot(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> root = new LinkedHashMap<>(objectMapper.readValue(rawJson, MAP_TYPE));
            removeInternalFields(root);
            return root;
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR,
                    "历史投递事件复盘数据格式异常，请先修复后再生成");
        }
    }

    public JobApplicationEventStructuredReviewVO readStructuredReview(String rawJson) {
        return readStructuredReview(readRoot(rawJson));
    }

    public JobApplicationEventStructuredReviewVO readStructuredReview(Map<String, Object> root) {
        Object value = root == null ? null : root.get("structuredReview");
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, JobApplicationEventStructuredReviewVO.class);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String mergeStructuredReview(String rawJson, JobApplicationEventStructuredReviewVO structuredReview) {
        Map<String, Object> root = readRoot(rawJson);
        root.put("structuredReview", structuredReview);
        return write(root);
    }

    public String write(Object value) {
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            removeInternalFields(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (Exception ex) {
            throw new IllegalStateException("投递事件复盘序列化失败", ex);
        }
    }

    public int utf8Size(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private void removeInternalFields(Object value) {
        if (value instanceof JsonNode node) {
            removeInternalFields(node);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.entrySet().removeIf(entry -> isRawResponseField(entry.getKey()));
            map.values().forEach(this::removeInternalFields);
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(this::removeInternalFields);
        }
    }

    private void removeInternalFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> internalFields = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                if (isRawResponseField(fieldName)) {
                    internalFields.add(fieldName);
                }
            });
            internalFields.forEach(objectNode::remove);
            objectNode.elements().forEachRemaining(this::removeInternalFields);
            return;
        }
        if (node.isArray()) {
            node.elements().forEachRemaining(this::removeInternalFields);
        }
    }

    private boolean isRawResponseField(Object key) {
        if (key == null) {
            return false;
        }
        String normalized = String.valueOf(key)
                .replace("_", "")
                .replace("-", "");
        return "rawresponse".equalsIgnoreCase(normalized);
    }
}
