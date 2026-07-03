package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_skill_evidence")
public class ProjectSkillEvidence extends BaseEntity {

    private Long userId;
    private Long projectEvidenceId;
    private String skillName;
    private String skillCategory;
    private String evidenceText;
    private String strengthLevel;
    private String jdKeyword;
    private String riskPoints;
    private String sourceType;
    private Integer confirmed;
}
