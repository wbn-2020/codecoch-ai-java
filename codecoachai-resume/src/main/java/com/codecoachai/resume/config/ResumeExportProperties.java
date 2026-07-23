package com.codecoachai.resume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Resume export and upload admission settings.
 *
 * <p>Concurrency and permit wait values are captured when the resume service
 * starts. Changing them requires restarting the resume service; the semaphore
 * is intentionally not resized during dynamic configuration refresh.
 */
@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.resume.export")
public class ResumeExportProperties {

    private static final long MAX_ARTIFACT_BYTES = 10L * 1024L * 1024L;

    private long maxSourceTextBytes = 512L * 1024L;
    private long maxArtifactBytes = MAX_ARTIFACT_BYTES;
    private int maxZipEntries = 12;
    private int maxConcurrentUploads = 2;
    private long uploadAcquireTimeoutMillis = 250L;
    private String pdfFontPath;

    public long effectiveMaxSourceTextBytes() {
        return maxSourceTextBytes < 1 ? 512L * 1024L : maxSourceTextBytes;
    }

    public long effectiveMaxArtifactBytes() {
        return maxArtifactBytes < 1 ? MAX_ARTIFACT_BYTES : Math.min(maxArtifactBytes, MAX_ARTIFACT_BYTES);
    }

    public int effectiveMaxZipEntries() {
        return maxZipEntries < 1 ? 12 : Math.min(maxZipEntries, 32);
    }

    public int effectiveMaxConcurrentUploads() {
        return maxConcurrentUploads < 1 ? 2 : Math.min(maxConcurrentUploads, 16);
    }

    public long effectiveUploadAcquireTimeoutMillis() {
        return uploadAcquireTimeoutMillis < 0 ? 250L : Math.min(uploadAcquireTimeoutMillis, 5_000L);
    }
}
