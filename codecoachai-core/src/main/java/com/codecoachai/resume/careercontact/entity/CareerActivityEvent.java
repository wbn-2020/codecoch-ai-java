package com.codecoachai.resume.careercontact.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_activity_event")
public class CareerActivityEvent extends BaseEntity {
    private Long userId;
    private Long activityId;
    private String eventType;
    private LocalDateTime eventTime;
    private String idempotencyKeyHash;
    private String requestHash;
}
