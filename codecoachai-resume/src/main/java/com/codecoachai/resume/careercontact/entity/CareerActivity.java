package com.codecoachai.resume.careercontact.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_activity")
public class CareerActivity extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private Long contactId;
    private String activityType;
    private String channelType;
    private String subject;
    private String summary;
    private LocalDateTime occurredAt;
    private LocalDateTime nextFollowUpAt;
    private String status;
    private String idempotencyKeyHash;
    private String requestHash;
}
