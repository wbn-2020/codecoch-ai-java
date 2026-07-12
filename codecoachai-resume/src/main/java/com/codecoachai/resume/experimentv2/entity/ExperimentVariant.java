package com.codecoachai.resume.experimentv2.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_experiment_variant")
public class ExperimentVariant extends BaseEntity {
    private Long userId;
    private Long hypothesisId;
    private String variantCode;
    private String name;
    private String description;
    private String treatmentJson;
    private Integer allocationWeight;
    private Integer controlFlag;
}
