package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_evidence")
public class ProjectEvidence extends BaseEntity {

    private Long userId;
    private String title;
    private String role;
    private String startDate;
    private String endDate;
    private String background;
    private String responsibility;
    private String techStack;
    private String difficulty;
    private String solution;
    private String result;
    private String reflection;
    private Integer completenessScore;
    private String completenessStatus;
    private String missingFields;
    private Long sourceResumeId;
    private Long sourceResumeProjectId;
    private Long targetJobId;
}
