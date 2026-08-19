package com.codecoachai.resume.domain.vo;

import lombok.Data;

@Data
public class ReadinessRepairRecordVO {

    private Long snapshotId;
    private Long userId;
    private Long targetJobId;
    private Long regeneratedSnapshotId;
    private String status;
    private String reasonCode;
    private String beforeValidationStatus;
    private String afterValidationStatus;
    private String beforeSnapshotHash;
    private String afterSnapshotHash;
}
