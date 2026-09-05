package com.codecoachai.task.feign.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class JobDescriptionAnalysisVO {

    private Long id;
    private Long targetJobId;
    private Long userId;
    private Long aiCallLogId;
    private String parseStatus;
    private String parseErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
