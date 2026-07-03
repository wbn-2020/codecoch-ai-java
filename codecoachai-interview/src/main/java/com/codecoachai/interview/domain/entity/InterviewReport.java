package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interview_report")
public class InterviewReport extends BaseEntity {

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
    private String rubricScores;
    private String followUpTree;
    private String adviceEvidence;
    private String abilityProfileUpdates;
    private String reportContent;
    private LocalDateTime generatedAt;
    private String suggestions;
    private String failureReason;
    private String generationToken;
}
