package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeJobMatchSubmitVO {

    private Long reportId;
    private Long resumeId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private Long aiCallLogId;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
