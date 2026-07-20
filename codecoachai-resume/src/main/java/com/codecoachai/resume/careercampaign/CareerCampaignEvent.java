package com.codecoachai.resume.careercampaign;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_campaign_event")
public class CareerCampaignEvent extends BaseEntity {
    private Long userId;
    private Long campaignId;
    private String eventType;
    private String summary;
    private String idempotencyKeyHash;
    private LocalDateTime occurredAt;
}
