package com.codecoachai.resume.feign.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateInterviewPreparationAiVO {

    private String summary;
    private List<String> facts = new ArrayList<>();
    private List<String> limits = new ArrayList<>();
    private List<String> focusAreas = new ArrayList<>();
    private List<String> projectStories = new ArrayList<>();
    private List<String> practiceQuestions = new ArrayList<>();
    private List<String> checklist = new ArrayList<>();
    private List<String> schedule = new ArrayList<>();
    private List<String> nextActions = new ArrayList<>();
    private List<String> evidenceSources = new ArrayList<>();
    private String confidenceLevel;
    private Boolean fallback;
    private Long aiCallLogId;
    private String sourceHash;
    private String rawResponse;
}
