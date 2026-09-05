package com.codecoachai.task.feign.dto;

import lombok.Data;

@Data
public class ParseResumeDTO {
    private Long analysisRecordId;
    private Long userId;
    private String rawText;
    private String originalFilename;
    private String fileExt;
}
