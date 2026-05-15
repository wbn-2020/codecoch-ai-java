package com.codecoachai.question.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_review")
public class QuestionReview extends BaseEntity {

    private String batchId;
    private Long createdBy;
    private String reviewStatus;
    private Long aiCallLogId;
    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private String difficulty;
    private Integer experienceYears;
    private String rawAiResultJson;
    private String questionTitle;
    private String questionContent;
    private String referenceAnswer;
    private String analysis;
    private String followUpQuestionsJson;
    private String tagSuggestionsJson;
    private String categorySuggestion;
    private String groupSuggestion;
    private Long categoryId;
    private Long groupId;
    private String tagIdsJson;
    private String editedContentJson;
    private String rejectReason;
    private Long approvedQuestionId;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
}
