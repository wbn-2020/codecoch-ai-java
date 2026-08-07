package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_suggestion_adoption")
public class ResumeSuggestionAdoption extends BaseEntity {
    private Long userId;
    private Long resumeId;
    private Long optimizeRecordId;
    private Long resumeVersionId;
    private String suggestionType;
    private String status;
    private String note;
}
