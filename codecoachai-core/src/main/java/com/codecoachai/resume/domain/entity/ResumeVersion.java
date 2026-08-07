package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_version")
public class ResumeVersion extends BaseEntity {
    private Long userId;
    private Long resumeId;
    private Integer versionNo;
    private String versionName;
    private String snapshotJson;
    private String sourceType;
    private Long sourceId;
    private Integer currentFlag;
}
