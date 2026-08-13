package com.codecoachai.question.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.stereotype.Component;

/**
 * Applies process-wide Apache POI OOXML decompression limits.
 *
 * <p>POI exposes these limits as static settings, so the update is synchronized
 * and is also repeated immediately before each DOCX import.
 */
@Component
@RequiredArgsConstructor
public class QuestionDocxZipSecurity {

    private final QuestionImportProperties properties;

    @PostConstruct
    public void initialize() {
        apply();
    }

    public void apply() {
        synchronized (ZipSecureFile.class) {
            ZipSecureFile.setMinInflateRatio(properties.safeDocxMinInflateRatio());
            ZipSecureFile.setMaxEntrySize(properties.safeDocxMaxEntryBytes());
            ZipSecureFile.setMaxTextSize(properties.safeDocxMaxTextBytes());
            ZipSecureFile.setMaxFileCount(properties.safeDocxMaxFileCount());
        }
    }
}
