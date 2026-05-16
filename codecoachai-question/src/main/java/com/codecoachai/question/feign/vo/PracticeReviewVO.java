package com.codecoachai.question.feign.vo;

import lombok.Data;

@Data
public class PracticeReviewVO {

    private Long recordId;
    private Long questionId;
    private Long aiCallLogId;
    private Integer score;
    private String masteryStatus;
    private String comment;
    private String suggestions;
    private String knowledgePoints;
    private String rawResponse;
}
