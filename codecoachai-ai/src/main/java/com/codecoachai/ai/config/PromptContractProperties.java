package com.codecoachai.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.ai.prompt-contract")
public class PromptContractProperties {

    private boolean startupCheckEnabled = true;

    private boolean failFast = true;
}
