package com.codecoachai.task.feign.vo;

import lombok.Data;

@Data
public class ResumeAnalysisRawVO {

    private Long id;
    private Long fileId;
    private Long userId;
    private String rawText;
    private String originalFilename;
    private String fileExt;
    private String parseStatus;
}
