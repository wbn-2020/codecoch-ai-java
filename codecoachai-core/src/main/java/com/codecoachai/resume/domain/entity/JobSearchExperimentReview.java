package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_search_experiment_review")
public class JobSearchExperimentReview extends BaseEntity {

    private Long userId;
    private Long experimentId;
    private String factSummary;
    private String insightSummary;
    private String unsupportedConclusion;
    private String sampleWarning;
    private String nextAction;
    private String strategyJson;
    private String aiTraceId;
    private String confidenceLevel;
    private Integer demoFlag;
}
