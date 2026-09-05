package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class ResumeImportRepairRecordVO {

    private Long analysisRecordId;
    private Long resumeId;
    private String status;
    private String reasonCode;
    private String beforeValidationStatus;
    private String afterValidationStatus;
    private String beforeHash;
    private String afterHash;
    private boolean manualResumeReconciliationRequired;
}
