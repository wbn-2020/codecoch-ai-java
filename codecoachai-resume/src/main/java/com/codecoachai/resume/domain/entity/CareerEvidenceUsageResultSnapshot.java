package com.codecoachai.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("career_evidence_usage_result_snapshot")
public class CareerEvidenceUsageResultSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resultId;
    private Long userId;
    private Integer snapshotVersion;
    private String status;
    private String outcomeCode;
    private String knownFactsJson;
    private String externalFeedbackText;
    private String userInterpretationText;
    private String unknownsJson;
    private String limitsJson;
    private String sourceType;
    private Long sourceId;
    private String sourceVersion;
    private String sourceHash;
    private LocalDateTime occurredAt;
    private LocalDateTime confirmedAt;
    private String contentHash;
    private String idempotencyKeyHash;
    private String idempotencyPayloadHash;
    private Long supersedesSnapshotId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
