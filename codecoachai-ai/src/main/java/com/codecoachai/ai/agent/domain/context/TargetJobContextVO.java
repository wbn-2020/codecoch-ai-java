package com.codecoachai.ai.agent.domain.context;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TargetJobContextVO {

    private Long id;
    private Long userId;
    private String jobTitle;
    private String companyName;
    private String jobLevel;
    private String jdText;
    private String jdSource;
    private Integer currentFlag;
    private Integer status;
    private String parseStatus;
    private String parseErrorMessage;
    private String analysisSummary;
    private JsonNode requiredSkills;
    private JsonNode interviewFocusPoints;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
