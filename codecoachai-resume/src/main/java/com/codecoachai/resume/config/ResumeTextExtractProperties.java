package com.codecoachai.resume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.resume.text-extract")
public class ResumeTextExtractProperties {

    private int maxExtractedTextChars = 30000;

    private int maxSourceFileSizeMb = 10;

    private int maxPdfPages = 10;

    public long maxSourceFileBytes() {
        int effectiveMb = maxSourceFileSizeMb <= 0 ? 10 : maxSourceFileSizeMb;
        return effectiveMb * 1024L * 1024L;
    }
}
