package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class ResumeUploadVO {

    private Long fileId;
    private Long analysisRecordId;
    private Long resumeId;
    private String parseStatus;
    private String originalFilename;
    private Long fileSize;
    private String fileExt;
    private String message;
}
