package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ResumeArtifactVO {
    private Long id;
    private String artifactType;
    private Long sourceResumeId;
    private Long sourceResumeVersionId;
    private Long sourceApplicationPackageId;
    private String sourceHash;
    private String templateCode;
    private Integer templateVersion;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private String status;
    private JsonNode manifest;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
