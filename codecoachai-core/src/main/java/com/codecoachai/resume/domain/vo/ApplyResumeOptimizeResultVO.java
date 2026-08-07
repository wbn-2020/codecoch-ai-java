package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ApplyResumeOptimizeResultVO {

    private Long sourceResumeId;
    private Long sourceOptimizeRecordId;
    private Long newResumeId;
    private LocalDateTime appliedAt;
    private String applyMode;
    private String message;
    private List<String> appliedFields;
    private List<String> skippedFields;
    private JsonNode fieldDiff;
    private List<String> warnings;
    private ResumeDetailVO resumeDetail;
}
