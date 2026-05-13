package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_project")
public class ResumeProject extends BaseEntity {

    private Long resumeId;
    private String projectName;
    private String role;
    private String techStack;
    private String description;
    private String highlights;
    private Integer sort;
}
