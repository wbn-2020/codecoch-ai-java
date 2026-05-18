package com.codecoachai.question.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class QuestionRecommendationGenerateVO {

    private Long batchId;
    private String status;
    private Integer questionCount;
    private Long aiCallLogId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
