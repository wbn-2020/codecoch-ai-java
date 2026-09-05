package com.codecoachai.resume.experimentv2.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_experiment_hypothesis")
public class ExperimentHypothesis extends BaseEntity {
    private Long userId;
    private Long legacyExperimentId;
    private String name;
    private String statement;
    private String primaryMetric;
    private String status;
    private Integer attributionWindowDays;
    private Integer minSamplePerVariant;
    private String allocationSalt;
}
