package com.codecoachai.resume.domain.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class InnerJobRequirementReadinessContextVO {

    private Long targetJobId;
    private Long jdAnalysisId;
    private Long snapshotId;
    private String snapshotHash;
    private String policyVersion;
    private LocalDateTime generatedAt;
    private Integer readinessScore;
    private String readinessLevel;
    private String confidenceLevel;
    private Boolean fallback;
    private Boolean matrixCurrent;
    private Boolean sampleSufficient;
    private Integer requirementCount;
    private List<String> warnings = new ArrayList<>();
    private List<RequirementContextItemVO> missingRequirements = new ArrayList<>();

    @Data
    public static class RequirementContextItemVO {
        private Long requirementId;
        private String requirementKey;
        private String requirementType;
        private String requirementName;
        private String priority;
        private String coverageLevel;
        private String confidenceLevel;
        private Boolean fallback;
        private List<Long> projectEvidenceIds = new ArrayList<>();
    }
}
