package com.codecoachai.ai.agent.weekly.support;

import com.codecoachai.common.core.util.TextFingerprintUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WeeklyReportHashUtils {

    private final WeeklyReportJsonCodec jsonCodec;

    public String hash(Object value) {
        return TextFingerprintUtils.sha256Hex(jsonCodec.toJson(value));
    }

    public String hashText(String value) {
        return StringUtils.hasText(value)
                ? TextFingerprintUtils.sha256Hex(value.trim())
                : null;
    }

    public String idempotencyHash(String operation, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return TextFingerprintUtils.sha256Hex(operation + ":" + key.trim());
    }
}
