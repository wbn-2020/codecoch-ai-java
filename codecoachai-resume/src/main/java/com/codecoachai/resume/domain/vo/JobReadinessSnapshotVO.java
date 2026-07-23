package com.codecoachai.resume.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class JobReadinessSnapshotVO {

    private Long id;
    private Long targetJobId;
    private Long jdAnalysisId;
    private String snapshotHash;
    private String policyVersion;
    private Integer readinessScore;
    private String readinessLevel;
    private String confidenceLevel;
    private Boolean fallback;
    private Boolean sampleInsufficient;
    private Integer requirementCount;
    private Integer strongCount;
    private Integer weakCount;
    private Integer missingCount;
    private Integer mustRequirementCount;
    private Integer mustMissingCount;
    private JsonNode summary;
    private JsonNode matrix;
    private List<DimensionScore> dimensions = new ArrayList<>();
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;

    @Data
    public static class DimensionScore {

        private String dimension;
        private Integer score;
        private Integer sampleCount;
        private String confidenceLevel;
        private Boolean fallback;
        private Boolean sampleInsufficient;
    }
}
