package com.codecoachai.interview.scenario;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScenarioBindingCreateDTO {

    @NotNull
    private Long scenarioVersionId;
    private String bindingSource = "USER_SELECTED";
}
