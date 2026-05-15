package com.codecoachai.resume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.resume.parse-task")
public class ResumeParseTaskProperties {

    private boolean enabled = true;
    private long fixedDelay = 10000L;
    private int batchSize = 5;
}
