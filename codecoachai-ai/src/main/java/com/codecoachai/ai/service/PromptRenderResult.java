package com.codecoachai.ai.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromptRenderResult {

    private String scene;
    private String renderedPrompt;
    private Long promptTemplateId;
    private Long promptTemplateVersionId;
    private String promptVersion;
    private String inputVariablesJson;
    private String modelParamsJson;
    private String promptHash;
    private Boolean fallbackUsed;
}
