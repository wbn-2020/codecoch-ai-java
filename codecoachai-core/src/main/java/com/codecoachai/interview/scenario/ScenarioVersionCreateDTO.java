package com.codecoachai.interview.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScenarioVersionCreateDTO {

    @NotBlank
    private String scenarioCode;
    @NotBlank
    private String scenarioName;
    private String description;
    private String locale = "zh-CN";
    @NotNull
    private JsonNode script;
    @NotNull
    private Long rubricVersionId;
}
