package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_story_generation")
public class ProjectStoryGeneration extends BaseEntity {

    private Long userId;
    private Long projectEvidenceId;
    private String generationType;
    private Long targetJobId;
    private String promptVersion;
    private String resultText;
    private String structuredResultJson;
    private String inputSummaryJson;
    private Long aiCallLogId;
    private String traceId;
    private String resultSource;
    private Integer accepted;
    private String status;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
