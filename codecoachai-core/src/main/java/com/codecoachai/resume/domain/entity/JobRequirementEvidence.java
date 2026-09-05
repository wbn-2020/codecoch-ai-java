package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_requirement_evidence")
public class JobRequirementEvidence extends BaseEntity {

    private Long userId;
    private Long targetJobId;
    private Long requirementId;
    private Long projectEvidenceId;
    private Long projectSkillEvidenceId;
    private String evidenceType;
    private Long evidenceId;
    private Long evidenceSubId;
    private String title;
    private String excerpt;
    private String resultSource;
    private Integer resultScore;
    private LocalDateTime occurredAt;
    private String evidenceRefKey;
    private String matchType;
    private String coverageLevel;
    private String confidenceLevel;
    private String evidenceSourceType;
    private Integer confirmed;
    private Integer fallback;
    private String evidenceText;
    private String matchReason;
    private Integer activeFlag;
}
