package com.codecoachai.resume.careeroffer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_offer_decision_item")
public class CareerOfferDecisionItem extends BaseEntity {
    private Long userId;
    private Long snapshotId;
    private Long offerId;
    private Long offerVersionId;
    private BigDecimal comparableAnnualValue;
    private BigDecimal weightedScore;
    private Integer rankNo;
    private String ruleResultJson;
    private String missingItemsJson;
}
