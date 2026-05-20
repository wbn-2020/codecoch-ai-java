package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_job_match_report")
public class ResumeJobMatchReport extends BaseEntity {

    private Long userId;
    private Long resumeId;
    private Long targetJobId;
    private Long jdAnalysisId;
    private Integer overallScore;
    private Integer techStackScore;
    private Integer projectExperienceScore;
    private Integer businessFitScore;
    private Integer communicationScore;
    private String strengthsJson;
    private String gapsJson;
    private String resumeRisksJson;
    private String optimizationSuggestionsJson;
    private String recommendedLearningTopicsJson;
    private String recommendedInterviewTopicsJson;
    private String summary;
    private String rawResultJson;
    private String status;
    private String errorMessage;
    private Long aiCallLogId;
}
