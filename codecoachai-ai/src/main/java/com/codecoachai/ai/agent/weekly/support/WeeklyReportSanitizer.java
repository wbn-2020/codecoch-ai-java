package com.codecoachai.ai.agent.weekly.support;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WeeklyReportSanitizer {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d\\s-]{7,}\\d)(?!\\d)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|authorization|token|secret|password)\\s*[:=]\\s*[^,\\s}]+");

    public String safeText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String safe = TOKEN_PATTERN.matcher(
                PHONE_PATTERN.matcher(
                        EMAIL_PATTERN.matcher(value.trim()).replaceAll("[邮箱已脱敏]"))
                        .replaceAll("[电话已脱敏]"))
                .replaceAll("[敏感信息已脱敏]");
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public String normalizeCode(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : fallback;
    }

    public String channelKey(String channelKey, String source) {
        String value = StringUtils.hasText(channelKey) ? channelKey : source;
        if (!StringUtils.hasText(value)) {
            return "CHANNEL:UNKNOWN";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("CHANNEL:") ? normalized : "CHANNEL:" + normalized;
    }

    public String resumeVersionKey(Long resumeVersionId) {
        return resumeVersionId == null
                ? "RESUME_VERSION:UNKNOWN"
                : "RESUME_VERSION:" + resumeVersionId;
    }
}
