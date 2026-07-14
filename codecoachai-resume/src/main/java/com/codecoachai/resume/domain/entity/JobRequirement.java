package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_requirement")
public class JobRequirement extends BaseEntity {

    private Long userId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private String requirementKey;
    private String requirementType;
    private String requirementName;
    private String description;
    private String category;
    private String requiredLevel;
    private String priority;
    private BigDecimal weight;
    private String confidenceLevel;
    private String sourceField;
    private String sourceJson;
    private Integer sourceFallback;
    private Integer activeFlag;
}
