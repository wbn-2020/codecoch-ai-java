package com.codecoachai.resume.careeroffer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_offer_event")
public class CareerOfferEvent extends BaseEntity {
    private Long userId;
    private Long offerId;
    private Long versionId;
    private String eventType;
    private String previousStatus;
    private String currentStatus;
    private LocalDateTime occurredAt;
    private String summary;
    private String idempotencyKeyHash;
    private String payloadHash;
}
