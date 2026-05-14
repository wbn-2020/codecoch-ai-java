package com.codecoachai.interview.domain.vo;

import lombok.Data;

@Data
public class InterviewReportVO {

    private Long id;
    private Long sessionId;
    private Long userId;
    private String status;
    private Integer totalScore;
    private String stageScores;
    private String weakPoints;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String mainProblems;
    private String projectProblems;
    private String reviewSuggestions;
    private String recommendedQuestions;
    private String qaReview;
    private String reportContent;
    private java.time.LocalDateTime generatedAt;
    private String suggestions;
    private String failureReason;
}
