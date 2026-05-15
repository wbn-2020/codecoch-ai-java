package com.codecoachai.ai.domain.dto;

import lombok.Data;

@Data
public class ParseResumeDTO {

    private Long analysisRecordId;
    private Long userId;
    private String rawText;
    private String originalFilename;
    private String fileExt;
}
