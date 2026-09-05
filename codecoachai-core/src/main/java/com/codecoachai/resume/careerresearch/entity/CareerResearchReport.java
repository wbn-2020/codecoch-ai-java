package com.codecoachai.resume.careerresearch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_research_report")
public class CareerResearchReport extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private Long currentSnapshotId;
    private String generationStatus;
    private String generationClaimToken;
    private LocalDateTime generationClaimedAt;
    private Integer lockVersion;
}
