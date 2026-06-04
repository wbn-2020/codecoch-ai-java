package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiResultFeedbackVO {

    private Long id;
    private Long userId;
    private String scene;
    private String bizType;
    private Long bizId;
    private Long aiCallLogId;
    private String feedbackType;
    private Integer rating;
    private String comment;
    private String pagePath;
    private LocalDateTime createdAt;
}
