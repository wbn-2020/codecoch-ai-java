package com.codecoachai.interview.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InterviewMessageVO {

    private Long id;
    private Long stageId;
    private Long questionId;
    private Long questionGroupId;
    private Long parentMessageId;
    private String role;
    private String messageType;
    private String content;
    private String questionContent;
    private String userAnswer;
    private String aiComment;
    private Integer aiScore;
    private Boolean isFollowUp;
    private Integer followUpCount;
    private String followUpReason;
    private String knowledgePoints;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
