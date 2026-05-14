package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiCallLogVO {

    private Long id;
    private Long userId;
    private String scene;
    private String modelName;
    private Long promptTemplateId;
    private String requestPrompt;
    private String responseContent;
    private String businessId;
    private Long elapsedMs;
    private Long costMillis;
    private Integer status;
    private String errorMessage;
    private String requestBody;
    private String responseBody;
    private LocalDateTime createdAt;
}
