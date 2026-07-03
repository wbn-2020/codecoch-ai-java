package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProjectStoryGenerationVO {

    private Long id;
    private Long userId;
    private Long projectEvidenceId;
    private String generationType;
    private Long targetJobId;
    private String promptVersion;
    private String resultText;
    private String structuredResultJson;
    private String inputSummaryJson;
    private Long aiCallLogId;
    private String traceId;
    private String resultSource;
    private Boolean accepted;
    private String status;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
