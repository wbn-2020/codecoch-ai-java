package com.codecoachai.ai.domain.vo;

import lombok.Data;

@Data
public class AnalyzeResumeJobMatchVO {

    private String resultJson;
    private Long aiCallLogId;
    private String rawResponse;
}
