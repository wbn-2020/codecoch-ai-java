package com.codecoachai.resume.domain.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ReadinessRepairResultVO {

    private boolean dryRun;
    private String repairBatchId;
    private int matchedRecords;
    private int processedRecords;
    private int changedRecords;
    private int manualActionRequired;
    private Map<String, Integer> statusCounts = new LinkedHashMap<>();
    private List<ReadinessRepairRecordVO> records = new ArrayList<>();
}
