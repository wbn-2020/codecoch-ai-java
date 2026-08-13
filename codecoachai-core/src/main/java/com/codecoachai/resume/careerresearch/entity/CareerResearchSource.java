package com.codecoachai.resume.careerresearch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_research_source")
public class CareerResearchSource extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private String sourceType;
    private String title;
    private String officialUrl;
    private String externalRef;
    private String status;
    private Long currentVersionId;
    private Integer lockVersion;
}
