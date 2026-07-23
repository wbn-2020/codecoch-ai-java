package com.codecoachai.resume.careerresearch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_research_snapshot_source")
public class CareerResearchSnapshotSource extends BaseEntity {
    private Long userId;
    private Long snapshotId;
    private Long sourceId;
    private Long sourceVersionId;
    private String contentHash;
}
