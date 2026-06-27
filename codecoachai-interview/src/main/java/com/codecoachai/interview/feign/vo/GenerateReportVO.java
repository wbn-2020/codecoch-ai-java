package com.codecoachai.interview.feign.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateReportVO {

    private Long aiCallLogId;
    private Integer totalScore;
    private String summary;
    private String stageScores;
    private String weakPoints;
    private String strengths;
    private String weaknesses;
    private String mainProblems;
    private String projectProblems;
    private String suggestions;
    private String reviewSuggestions;
    private String recommendedQuestions;
    private String qaReview;
    private String reportContent;
}
