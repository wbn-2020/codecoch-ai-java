package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_search_experiment_relation")
public class JobSearchExperimentRelation extends BaseEntity {

    private Long userId;
    private Long experimentId;
    private String relationType;
    private Long relationId;
    private String relationSummary;
    private String metadataJson;
    private Integer demoFlag;
}
