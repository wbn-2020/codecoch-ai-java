package com.codecoachai.ai.agent.domain.context;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobDescriptionAnalysisContextVO {

    private Long id;
    private Long targetJobId;
    private Long userId;
    private String status;
    private String summary;
    private String errorMessage;
    private JsonNode requiredSkills;
    private JsonNode interviewFocusPoints;
    private JsonNode keywords;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
