package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("practice_record")
public class PracticeRecord extends BaseEntity {

    private Long userId;
    private Long questionId;
    private String answerContent;
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
    private String aiComment;
    private String suggestions;
    private String knowledgePoints;
    private String strengths;
    private String weaknesses;
    private String improvementSuggestions;
    private String referenceComparison;
    private String knowledgeGaps;
    private String suggestedFollowUps;
    private String referenceAnswerSnapshot;
    private String questionSnapshotJson;
    private String reviewJson;
    private Long aiCallLogId;
    private String errorMessage;
}
