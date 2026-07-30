package com.codecoachai.ai.service.impl;

import com.codecoachai.ai.config.PromptContractProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptSceneContractStartupVerifier implements ApplicationRunner {

    private final PromptContractProperties properties;
    private final PromptRenderServiceImpl promptRenderService;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isStartupCheckEnabled()) {
            promptRenderService.verifyActivePromptContracts(properties.isFailFast());
        }
    }
}
