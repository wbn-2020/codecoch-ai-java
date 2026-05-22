package com.codecoachai.ai.agent.domain.dto;

import lombok.Data;

@Data
public class PromptRegressionCaseSaveDTO {
    private Long id;
    private String caseName;
    private String promptType;
    private String inputJson;
    private String expectedSchemaJson;
    private Integer enabled;
}
