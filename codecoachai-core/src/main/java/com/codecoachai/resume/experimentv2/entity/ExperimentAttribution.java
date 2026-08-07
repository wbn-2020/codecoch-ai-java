package com.codecoachai.resume.experimentv2.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_experiment_attribution")
public class ExperimentAttribution extends BaseEntity {
    private Long userId;
    private Long hypothesisId;
    private Long cohortId;
    private LocalDateTime asOf;
    private LocalDateTime dataCutoffAt;
    private String inputHash;
    private String algorithmVersion;
    private String sourceWatermark;
    private String resultSource;
    private Integer fallback;
    private String method;
    private Integer comparableFlag;
    private Integer sampleCount;
    private Integer commonStrataCount;
    private String incomparableReasonsJson;
    private String limitationsJson;
    private String resultJson;
}
