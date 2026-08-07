package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_analysis_record")
public class ResumeAnalysisRecord extends BaseEntity {

    private Long userId;
    private Long resumeId;
    private Long fileId;
    private String sourceType;
    private String parseStatus;
    private String rawText;
    private String structuredJson;
    private String errorMessage;
}
