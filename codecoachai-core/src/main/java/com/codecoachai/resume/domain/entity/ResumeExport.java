package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_export")
public class ResumeExport extends BaseEntity {
    private Long userId;
    private Long resumeId;
    private Long resumeVersionId;
    private String sourceHash;
    private Long templateId;
    private String templateCode;
    private Integer templateVersion;
    private String exportFormat;
    private Long artifactId;
    private String status;
    private String contentHash;
    private String errorMessage;
}
