package com.codecoachai.resume.careerresearch.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_research_snapshot")
public class CareerResearchSnapshot extends BaseEntity {
    private Long userId;
    private Long reportId;
    private Long applicationId;
    private String sourceSetHash;
    private String generationClaimToken;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String snapshotJson;
    private String confidenceLevel;
    // V4_084 currently names this physical column `fallback`; keep the API/domain name explicit.
    @TableField("fallback")
    private String fallbackReason;
    private Long aiCallLogId;
}
