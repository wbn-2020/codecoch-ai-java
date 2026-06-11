package com.codecoachai.task.feign.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionRecommendationGenerateVO {

    private Long batchId;
    private String status;
    private Integer questionCount;
    private Long aiCallLogId;
    private String errorMessage;
    private String asyncMessageId;
    private String asyncTraceId;
    private String asyncBizType;
    private String asyncBizId;
    private String asyncSendStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
