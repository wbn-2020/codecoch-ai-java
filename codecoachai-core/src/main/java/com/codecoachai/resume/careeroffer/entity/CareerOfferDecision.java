package com.codecoachai.resume.careeroffer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_offer_decision")
public class CareerOfferDecision extends BaseEntity {
    private Long userId;
    private Long campaignId;
    private String status;
    private Long currentSnapshotId;
    private Long selectedOfferId;
    private String outcome;
    private Integer lockVersion;
    private LocalDateTime confirmedAt;
    private String idempotencyKeyHash;
    private String payloadHash;
}
