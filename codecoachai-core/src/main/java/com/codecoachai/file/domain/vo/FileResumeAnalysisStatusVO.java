package com.codecoachai.file.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FileResumeAnalysisStatusVO {

    private Long fileId;
    private Long resumeId;
    private Long resumeAnalysisRecordId;
    private String parseStatus;
    private String parseErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
