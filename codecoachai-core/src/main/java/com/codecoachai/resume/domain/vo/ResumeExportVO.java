package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeExportVO {
    private Long id;
    private Long resumeId;
    private Long resumeVersionId;
    private String sourceHash;
    private Long templateId;
    private String templateCode;
    private Integer templateVersion;
    private String exportFormat;
    private String status;
    private String contentHash;
    private String errorMessage;
    private ResumeArtifactVO artifact;
    private LocalDateTime createdAt;
}
