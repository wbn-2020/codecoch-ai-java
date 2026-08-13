package com.codecoachai.interview.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ScenarioVersionVO {

    private Long scenarioVersionId;
    private String scenarioCode;
    private Integer versionNo;
    private String scenarioName;
    private String description;
    private String locale;
    private JsonNode script;
    private Long rubricVersionId;
    private String versionStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
