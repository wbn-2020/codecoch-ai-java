package com.codecoachai.resume.experimentv2.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_experiment_cohort")
public class ExperimentCohort extends BaseEntity {
    private Long userId;
    private Long hypothesisId;
    private String name;
    private String jobFamily;
    private String channel;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String outcomeType;
    private Integer minSamplePerVariant;
}
