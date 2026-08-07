package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_job_match_detail")
public class ResumeJobMatchDetail extends BaseEntity {

    private Long reportId;
    private Long userId;
    private String dimension;
    private String skillName;
    private String matchLevel;
    private Integer score;
    private String evidence;
    private String gapDescription;
    private String suggestion;
}
