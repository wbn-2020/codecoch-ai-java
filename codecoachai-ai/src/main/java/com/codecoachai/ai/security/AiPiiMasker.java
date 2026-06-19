package com.codecoachai.ai.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * PII masker for outbound AI requests.
 * Masks personally identifiable information (phone, email, name) in resume data
 * before the data is sent to external AI providers.
 * <p>
 * Phone: keeps last 4 digits, e.g. "138****1234"
 * Email: keeps domain, e.g. "***@example.com"
 * Name: keeps surname (first character), e.g. "张*" for Chinese, "J***" for English
 */
public final class AiPiiMasker {

    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiPiiMasker() {
    }

    /**
     * Mask PII fields in a serialized JSON string.
     * Scans all text values for phone numbers and emails,
     * and specifically masks the "realName" field if present.
     */
    public static String maskResumeJson(String json) {
        if (!StringUtils.hasText(json)) {
            return json;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || root.isNull()) {
                return json;
            }
            JsonNode masked = maskNode(root);
            return OBJECT_MAPPER.writeValueAsString(masked);
        } catch (JsonProcessingException e) {
            // Not valid JSON -- apply regex masking directly on the raw string
            return maskTextValues(json);
        }
    }

    private static JsonNode maskNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = node.deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();
                if ("realName".equals(fieldName) && value.isTextual()) {
                    obj.put(fieldName, maskName(value.asText()));
                } else {
                    obj.set(fieldName, maskNode(value));
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = node.deepCopy();
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, maskNode(arr.get(i)));
            }
            return arr;
        }
        if (node.isTextual()) {
            return new TextNode(maskTextValues(node.asText()));
        }
        return node;
    }

    /**
     * Apply PII regex masking to a plain text string.
     * Masks phone numbers (keeping last 4 digits) and emails (keeping domain).
     */
    static String maskTextValues(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String masked = maskPhone(text);
        masked = maskEmail(masked);
        return masked;
    }

    /**
     * Mask Chinese mobile numbers: keeps last 4 digits.
     * e.g. "13812341234" -> "138****1234"
     */
    static String maskPhone(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return CHINA_MOBILE.matcher(text).replaceAll(mr -> {
            String number = mr.group();
            return number.substring(0, 3) + "****" + number.substring(7);
        });
    }

    /**
     * Mask email addresses: keeps domain (part after @).
     * e.g. "user@example.com" -> "***@example.com"
     */
    static String maskEmail(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return EMAIL.matcher(text).replaceAll("***@$1");
    }

    /**
     * Mask a real name: keeps the first character (surname) and replaces the rest with *.
     * Chinese name: "张三" -> "张*"
     * English name: "John" -> "J***"
     * Multi-character surname: "欧阳雪" -> "欧**"
     */
    static String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        String trimmed = name.trim();
        if (trimmed.length() <= 1) {
            return trimmed;
        }
        char first = trimmed.charAt(0);
        int restLength = trimmed.length() - 1;
        StringBuilder sb = new StringBuilder(trimmed.length());
        sb.append(first);
        for (int i = 0; i < restLength; i++) {
            sb.append('*');
        }
        return sb.toString();
    }
}
