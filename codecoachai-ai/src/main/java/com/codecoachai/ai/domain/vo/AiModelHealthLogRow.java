package com.codecoachai.ai.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AiModelHealthLogRow {

    private Long modelConfigId;
    private String healthBucket;
    private Integer success;
    private Integer status;
    private LocalDateTime createdAt;
    private String errorMessage;
}
