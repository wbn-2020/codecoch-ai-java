package com.codecoachai.task.feign.vo;

import lombok.Data;

@Data
public class GenerateReportVO {
    private String reportJson;
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
