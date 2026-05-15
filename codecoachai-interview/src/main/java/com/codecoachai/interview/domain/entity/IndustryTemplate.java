package com.codecoachai.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("industry_template")
public class IndustryTemplate extends BaseEntity {

    private String industryCode;
    private String industryName;
    private String description;
    private String targetPositions;
    private String coreBusinessScenarios;
    private String keyTechnicalPoints;
    private String commonQuestionDirections;
    private String riskPoints;
    private String promptContext;
    private Integer enabled;
    private Integer sortOrder;
}
