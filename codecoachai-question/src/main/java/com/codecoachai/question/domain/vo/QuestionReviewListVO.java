package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionReviewListVO {

    private Long id;
    private String batchId;
    private String reviewStatus;
    private Long aiCallLogId;
    private String targetPosition;
    private String technologyStack;
    private String knowledgePoint;
    private String questionType;
    private String difficulty;
    private Integer experienceYears;
    private String questionTitle;
    private Long categoryId;
    private Long groupId;
    private Long approvedQuestionId;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
