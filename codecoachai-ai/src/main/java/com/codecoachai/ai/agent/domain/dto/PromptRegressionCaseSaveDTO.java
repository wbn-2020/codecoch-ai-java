package com.codecoachai.ai.agent.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class PromptRegressionCaseSaveDTO {
    private Long id;
    @JsonAlias("name")
    private String caseName;
    @JsonAlias({"type", "promptCode"})
    private String promptType;
    @JsonAlias({"input", "requestJson"})
    private String inputJson;
    @JsonAlias({"schema", "expectedJson", "expectedOutputSchema"})
    private String expectedSchemaJson;
    private Integer enabled;
}
