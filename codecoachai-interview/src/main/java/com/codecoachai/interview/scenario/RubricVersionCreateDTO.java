package com.codecoachai.interview.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RubricVersionCreateDTO {

    @NotBlank
    private String rubricCode;
    @NotBlank
    private String rubricName;
    private String description;
    private String locale = "zh-CN";
    @NotNull
    private JsonNode dimensions;
}
