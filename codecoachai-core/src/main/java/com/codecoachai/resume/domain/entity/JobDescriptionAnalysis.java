package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_description_analysis")
public class JobDescriptionAnalysis extends BaseEntity {

    private Long targetJobId;
    private Long userId;
    private String jobTitle;
    private String companyName;
    private String jobLevel;
    private String responsibilitiesJson;
    private String requiredSkillsJson;
    private String bonusSkillsJson;
    private String techKeywordsJson;
    private String businessKeywordsJson;
    private String experienceRequirement;
    private String projectExperienceRequirement;
    private String interviewFocusJson;
    private String skillWeightsJson;
    private String summary;
    private String rawResultJson;
    private Long aiCallLogId;
    private String parseStatus;
    private String parseErrorMessage;
}
