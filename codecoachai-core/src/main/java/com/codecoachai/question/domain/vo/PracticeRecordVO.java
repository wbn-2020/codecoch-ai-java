package com.codecoachai.question.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PracticeRecordVO {

    private Long id;
    private Long userId;
    private Long questionId;
    private String questionTitle;
    private String questionType;
    private String difficulty;
    private String knowledgePoint;
    private String answerContent;
    private String userAnswer;
    private Integer answerDurationSeconds;
    private String source;
    private Long recommendationItemId;
    private Long batchId;
    private String sourceType;
    private Long sourceId;
    private Long skillProfileId;
    private Long studyPlanId;
    private String reviewStatus;
    private Integer score;
    private String level;
    private String masteryStatus;
    private String summary;
    private String aiComment;
    private String suggestions;
    private String knowledgePoints;
    private String strengths;
    private String weaknesses;
    private String improvementSuggestions;
    private String referenceComparison;
    private String knowledgeGaps;
    private String suggestedFollowUps;
    private String referenceAnswer;
    private String referenceAnswerSnapshot;
    @JsonIgnore
    private String questionSnapshotJson;
    @JsonIgnore
    private String reviewJson;
    private Long aiCallLogId;
    private String errorMessage;
    private Boolean agentTaskCompleted;
    private Long agentTaskId;
    private String agentTaskTitle;
    private String agentTaskStatus;
    private String agentReviewSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
