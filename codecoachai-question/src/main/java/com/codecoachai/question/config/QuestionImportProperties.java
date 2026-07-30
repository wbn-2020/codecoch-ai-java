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
    private double docxMinInflateRatio = 0.01d;
    private long docxMaxEntryBytes = 32L * MB;
    private long docxMaxTextBytes = 16L * MB;
    private long docxMaxFileCount = 1_000L;

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

    public double safeDocxMinInflateRatio() {
        if (!Double.isFinite(docxMinInflateRatio)) {
            return 0.01d;
        }
        return Math.max(0.01d, Math.min(docxMinInflateRatio, 1.0d));
    }

    public long safeDocxMaxEntryBytes() {
        return Math.max(docxMaxEntryBytes, KB);
    }

    public long safeDocxMaxTextBytes() {
        return Math.max(docxMaxTextBytes, KB);
    }

    public long safeDocxMaxFileCount() {
        return Math.max(1L, Math.min(docxMaxFileCount, 10_000L));
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
