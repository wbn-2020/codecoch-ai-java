package com.codecoachai.resume.careeroffer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_offer")
public class CareerOffer extends BaseEntity {
    private Long userId;
    private Long applicationId;
    private Long currentVersionId;
    private String status;
    private Integer lockVersion;
    private Integer nextVersionNo;
    private LocalDateTime decisionDeadline;
    private LocalDateTime finalizedAt;
    private String idempotencyKeyHash;
    private String payloadHash;
}
