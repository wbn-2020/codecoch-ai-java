package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_import_repair_audit")
public class ResumeImportRepairAudit extends BaseEntity {

    private String repairBatchId;
    private Long analysisRecordId;
    private Long userId;
    private Long resumeId;
    private Long actorUserId;
    private String operation;
    private String status;
    private String beforeSnapshotCiphertext;
    private String afterSnapshotCiphertext;
    private String beforeHash;
    private String afterHash;
    private String beforeValidationStatus;
    private String afterValidationStatus;
    private String reasonCode;
    private String note;
}
