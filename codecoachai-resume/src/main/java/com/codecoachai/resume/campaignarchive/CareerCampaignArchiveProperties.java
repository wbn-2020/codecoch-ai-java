package com.codecoachai.resume.campaignarchive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "codecoachai.resume.campaign-archive")
public class CareerCampaignArchiveProperties {

    private long maxEntryBytes = 2L * 1024L * 1024L;
    private long maxTotalUncompressedBytes = 8L * 1024L * 1024L;
    private long maxArchiveBytes = 10L * 1024L * 1024L;
    private int maxEntries = 13;
    private int maxRowsPerSection = 5000;
    private int maxErrorMessageChars = 1000;

    public long effectiveMaxEntryBytes() {
        return maxEntryBytes < 1 ? 2L * 1024L * 1024L : Math.min(maxEntryBytes, 10L * 1024L * 1024L);
    }

    public long effectiveMaxTotalUncompressedBytes() {
        return maxTotalUncompressedBytes < 1
                ? 8L * 1024L * 1024L
                : Math.min(maxTotalUncompressedBytes, 10L * 1024L * 1024L);
    }

    public long effectiveMaxArchiveBytes() {
        return maxArchiveBytes < 1
                ? 10L * 1024L * 1024L
                : Math.min(maxArchiveBytes, 10L * 1024L * 1024L);
    }

    public int effectiveMaxEntries() {
        return maxEntries < 13 ? 13 : Math.min(maxEntries, 64);
    }

    public int effectiveMaxRowsPerSection() {
        return maxRowsPerSection < 1 ? 5000 : Math.min(maxRowsPerSection, 10000);
    }

    public int effectiveMaxErrorMessageChars() {
        return maxErrorMessageChars < 1 ? 1000 : Math.min(maxErrorMessageChars, 4000);
    }
}
