package com.codecoachai.ai.service;

import java.util.Map;

public interface PromptRenderService {

    PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables);

    PromptRenderResult render(String scene, String fallbackContent, Map<String, String> variables,
                              String prefix, String suffix);
}
