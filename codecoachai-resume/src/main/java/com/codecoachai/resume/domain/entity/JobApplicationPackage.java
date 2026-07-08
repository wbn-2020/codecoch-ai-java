package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_application_package")
public class JobApplicationPackage extends BaseEntity {

    private Long userId;
    private String packageNo;
    private Long targetJobId;
    private Long jdAnalysisId;
    private Long resumeId;
    private Long resumeVersionId;
    private Long matchReportId;
    private Long applicationId;
    private String companyName;
    private String jobTitle;
    private String readinessLevel;
    private Integer readinessScore;
    private String readinessReason;
    private String packageStatus;
    private String snapshotJson;
    private String checklistJson;
    private String actionsJson;
    private String projectEvidenceIdsJson;
    private String traceId;
    private String resultSource;
    private Integer fallback;
    private String fallbackReason;
    private Integer snapshotVersion;
    private LocalDateTime refreshedAt;
}
