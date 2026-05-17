package com.codecoachai.question.feign.vo;

import java.util.List;
import lombok.Data;

@Data
public class PracticeReviewVO {

    private Long recordId;
    private Long questionId;
    private Long aiCallLogId;
    private Integer score;
    private String level;
    private String masteryStatus;
    private String summary;
    private String comment;
    private String suggestions;
    private String knowledgePoints;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> improvementSuggestions;
    private String referenceComparison;
    private List<String> knowledgeGaps;
    private List<String> suggestedFollowUps;
    private String rawResponse;
}
