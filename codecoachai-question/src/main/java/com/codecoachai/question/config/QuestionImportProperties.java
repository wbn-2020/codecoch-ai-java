package com.codecoachai.question.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.question.import")
public class QuestionImportProperties {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;

    private long maxFileBytes = 10L * MB;
    private int maxTitleChars = 500;
    private int maxFieldChars = 100_000;
    private int maxEntryChars = 200_000;

    public long safeMaxFileBytes() {
        return Math.max(maxFileBytes, 1L);
    }

    public int safeMaxTitleChars() {
        return Math.max(maxTitleChars, 1);
    }

    public int safeMaxFieldChars() {
        return Math.max(maxFieldChars, 1);
    }

    public int safeMaxEntryChars() {
        return Math.max(maxEntryChars, 1);
    }

    public String maxFileSizeLabel() {
        long bytes = safeMaxFileBytes();
        if (bytes % MB == 0) {
            return (bytes / MB) + "MB";
        }
        if (bytes % KB == 0) {
            return (bytes / KB) + "KB";
        }
        return bytes + "B";
    }
}
