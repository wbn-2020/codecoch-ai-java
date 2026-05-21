package com.codecoachai.ai.agent.domain.vo.ops;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PromptRegressionCaseVO {
    private Long id;
    private String caseName;
    private String promptType;
    private String inputJson;
    private String expectedSchemaJson;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
