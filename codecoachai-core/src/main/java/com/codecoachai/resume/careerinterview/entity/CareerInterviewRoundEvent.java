package com.codecoachai.resume.careerinterview.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_interview_round_event")
public class CareerInterviewRoundEvent extends BaseEntity {
    private Long userId;
    private Long processId;
    private Long roundId;
    private String eventType;
    private String previousStatus;
    private String currentStatus;
    private String payloadJson;
    private String idempotencyKeyHash;
    private LocalDateTime occurredAt;
}
