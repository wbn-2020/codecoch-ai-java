package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_analysis_record")
public class ResumeAnalysisRecord extends BaseEntity {

    private Long userId;
    private Long resumeId;
    private Long fileId;
    private String sourceType;
    private String parseStatus;
    private String rawText;
    private String structuredJson;
    private String schemaVersion;
    private String policyVersion;
    private String sourceHash;
    private String validationStatus;
    private String qualityReportJson;
    private LocalDateTime generatedAt;
    private String repairBatchId;
    private String errorMessage;
}
