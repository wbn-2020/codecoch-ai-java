package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("job_application_package_snapshot")
public class JobApplicationPackageSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long packageId;
    private Long userId;
    private Integer snapshotVersion;
    private String snapshotJson;
    private String checklistJson;
    private String actionsJson;
    private String projectEvidenceIdsJson;
    private Long resumeVersionId;
    private Long matchReportId;
    private String contentHash;
    private LocalDateTime capturedAt;
    private String captureSource;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
