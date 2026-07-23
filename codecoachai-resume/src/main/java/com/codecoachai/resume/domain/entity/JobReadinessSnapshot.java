package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_readiness_snapshot")
public class JobReadinessSnapshot extends BaseEntity {

    private Long userId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private String snapshotHash;
    private String policyVersion;
    private Integer readinessScore;
    private String readinessLevel;
    private String confidenceLevel;
    private Integer fallback;
    private Integer requirementCount;
    private Integer strongCount;
    private Integer weakCount;
    private Integer missingCount;
    private Integer mustRequirementCount;
    private Integer mustMissingCount;
    private String summaryJson;
    private String matrixJson;
    private String dimensionJson;
    private LocalDateTime generatedAt;
}
