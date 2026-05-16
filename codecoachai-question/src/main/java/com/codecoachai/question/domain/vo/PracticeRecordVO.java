package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PracticeRecordVO {

    private Long id;
    private Long userId;
    private Long questionId;
    private String questionTitle;
    private String answerContent;
    private String reviewStatus;
    private Integer score;
    private String masteryStatus;
    private String aiComment;
    private String suggestions;
    private String knowledgePoints;
    private String referenceAnswer;
    private Long aiCallLogId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
