package com.codecoachai.interview.scenario;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ScenarioBindingVO {

    private Long bindingId;
    private Long sessionId;
    private Long scenarioVersionId;
    private Long rubricVersionId;
    private String bindingSource;
    private LocalDateTime createdAt;
}
