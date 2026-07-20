package com.codecoachai.resume.careerresearch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_research_source_version")
public class CareerResearchSourceVersion extends BaseEntity {
    private Long userId;
    private Long sourceId;
    private String versionToken;
    private String contentHash;
    private String contentSummary;
    private String contentText;
    private LocalDateTime capturedAt;
}
