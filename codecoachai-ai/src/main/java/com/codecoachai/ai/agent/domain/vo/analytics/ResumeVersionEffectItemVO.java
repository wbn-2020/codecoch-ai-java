package com.codecoachai.ai.agent.domain.vo.analytics;

import lombok.Data;

@Data
public class ResumeVersionEffectItemVO {

    private Long resumeId;
    private Long resumeVersionId;
    private Integer versionNo;
    private String versionName;
    private Integer currentFlag;
    private Long applicationCount = 0L;
    private Long interviewCount = 0L;
    private Long offerCount = 0L;
    private String sampleLevel;
    private String insightLabel;
}
