package com.codecoachai.ai.domain.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateInterviewPreparationDTO {

    private Long userId;
    private Long calendarEventId;
    private Long applicationId;
    private Integer timeBudgetMinutes;
    private String eventTitle;
    private String eventDescription;
    private String eventType;
    private String eventLocalTime;
    private String timezone;
    private String location;
    private String companyName;
    private String jobTitle;
    private String targetJobSummary;
    private List<String> jobRequirements = new ArrayList<>();
    private String resumeVersionSummary;
    private List<String> projectEvidence = new ArrayList<>();
    private List<String> readinessGaps = new ArrayList<>();
    private List<String> recentInterviewWeaknesses = new ArrayList<>();
    private List<String> confirmedMemories = new ArrayList<>();
    private List<String> sourceWarnings = new ArrayList<>();
    private String sourceHash;
}
