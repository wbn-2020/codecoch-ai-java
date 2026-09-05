package com.codecoachai.resume.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class ResumeImportRepairRollbackDTO {

    private List<Long> auditIds;
    private Integer maxRecords;
    private Boolean dryRun = Boolean.TRUE;
    private Boolean confirm;
    private String reason;
    private String idempotencyKey;
}
