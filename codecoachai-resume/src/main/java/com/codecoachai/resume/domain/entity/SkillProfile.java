package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("skill_profile")
public class SkillProfile extends BaseEntity {

    private Long userId;
    private Long targetJobId;
    private Long matchReportId;
    private String profileName;
    private Integer overallLevel;
    private Integer overallScore;
    private String summary;
    private String sourceType;
    private Long sourceBizId;
    private String status;
    private String rawResultJson;
    private Long aiCallLogId;
    private String errorMessage;
}
