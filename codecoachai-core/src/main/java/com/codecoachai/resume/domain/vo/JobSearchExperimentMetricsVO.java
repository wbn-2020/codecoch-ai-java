package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class JobSearchExperimentMetricsVO {

    private Integer applicationCount = 0;
    private Integer feedbackCount = 0;
    private Integer interviewInviteCount = 0;
    private Integer interviewCompletedCount = 0;
    private Integer offerCount = 0;
    private Integer rejectedCount = 0;
    private Integer resumeVersionCount = 0;
    private Integer targetJobCount = 0;
    private Integer projectEvidenceCount = 0;
    private Integer agentTaskCount = 0;
    private Integer sampleCount = 0;
    private String confidenceLevel = "LOW";
    private Boolean sampleInsufficient = true;
    private Boolean resumeVersionSampleInsufficient = true;
    private String sampleWarning;
    private List<String> facts = new ArrayList<>();
    private List<String> unsupportedConclusions = new ArrayList<>();
    private List<String> weakObservations = new ArrayList<>();
    private Map<String, Object> sampleBoundary = new LinkedHashMap<>();
    private Map<Long, Integer> resumeVersionUsageCounts = new LinkedHashMap<>();
}
