package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeParseStatusVO {

    private Long analysisRecordId;
    private Long resumeId;
    private Long fileId;
    private String parseStatus;
    private String errorMessage;
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
