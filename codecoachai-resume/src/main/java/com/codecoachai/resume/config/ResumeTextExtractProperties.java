package com.codecoachai.resume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.resume.text-extract")
public class ResumeTextExtractProperties {

    private int maxExtractedTextChars = 30000;
}
