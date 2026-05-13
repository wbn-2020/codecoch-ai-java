package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiCallLogVO {

    private Long id;
    private String scene;
    private Long costMillis;
    private Integer status;
    private String errorMessage;
    private String requestBody;
    private String responseBody;
    private LocalDateTime createdAt;
}
