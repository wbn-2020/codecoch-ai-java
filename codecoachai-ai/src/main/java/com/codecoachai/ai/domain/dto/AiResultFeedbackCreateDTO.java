package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class AiResultFeedbackCreateDTO {

    private String scene;
    private String bizType;
    private Long bizId;
    private Long aiCallLogId;
    private String feedbackType;
    private Integer rating;
    private String comment;
    private String pagePath;
}
