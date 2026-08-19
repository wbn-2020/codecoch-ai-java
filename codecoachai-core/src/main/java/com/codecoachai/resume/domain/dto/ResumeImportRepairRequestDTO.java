package com.codecoachai.resume.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class ResumeImportRepairRequestDTO {

    private List<Long> analysisRecordIds;
    private List<Long> resumeIds;
    private List<Long> userIds;
    private Integer maxRecords;
    private String repairBatchId;
    private Boolean dryRun = Boolean.TRUE;
    private Boolean confirm;
    private String reason;
    private String idempotencyKey;
}
