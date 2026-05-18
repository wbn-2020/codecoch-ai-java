package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobDescriptionAnalysisVO {

    private Long id;
    private Long targetJobId;
    private Long userId;
    private String jobTitle;
    private String companyName;
    private String jobLevel;
    private JsonNode responsibilities;
    private JsonNode requiredSkills;
    private JsonNode bonusSkills;
    private JsonNode techStackKeywords;
    private JsonNode businessKeywords;
    private String experienceRequirement;
    private String projectExperienceRequirement;
    private JsonNode interviewFocusPoints;
    private JsonNode skillWeights;
    private String summary;
    private JsonNode rawResult;
    private Long aiCallLogId;
    private String parseStatus;
    private String parseErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
