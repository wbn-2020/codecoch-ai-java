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
@TableName("project_evidence_version")
public class ProjectEvidenceVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectEvidenceId;
    private Long userId;
    private Integer versionNo;
    private String snapshotJson;
    private String contentHash;
    private String sourceType;
    private Long sourceId;
    private LocalDateTime confirmedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
