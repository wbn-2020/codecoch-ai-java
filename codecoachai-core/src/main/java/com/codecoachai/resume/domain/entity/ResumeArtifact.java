package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_artifact")
public class ResumeArtifact extends BaseEntity {
    private Long userId;
    private String artifactType;
    private Long sourceResumeId;
    private Long sourceResumeVersionId;
    private Long sourceApplicationPackageId;
    private String sourceHash;
    private String templateCode;
    private Integer templateVersion;
    private Long fileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private String status;
    private String manifestJson;
    private String errorMessage;
}
