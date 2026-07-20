package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_application")
public class JobApplication extends BaseEntity {
    private Long userId;
    private Long campaignId;
    private Long targetJobId;
    private Long resumeVersionId;
    private Long matchReportId;
    private String companyName;
    private String jobTitle;
    private String source;
    private String status;
    private LocalDateTime stageChangedAt;
    private String priorityLevel;
    private String opportunityOutcome;
    private Integer lockVersion;
    private LocalDateTime appliedAt;
    private LocalDateTime nextFollowUpAt;
    private String note;
    private String importFingerprint;
}
