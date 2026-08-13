package com.codecoachai.resume.careeroffer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_offer_decision_snapshot")
public class CareerOfferDecisionSnapshot extends BaseEntity {
    private Long userId;
    private Long decisionId;
    private Long campaignId;
    private Integer snapshotNo;
    private String comparisonCurrency;
    private Integer comparable;
    private String weightsJson;
    private String ruleResultJson;
    private String missingItemsJson;
    private String limitationsJson;
    private String exchangeRatesJson;
    private String exchangeRateSource;
    private LocalDateTime exchangeRateDate;
    private String aiExplanation;
    private Long aiCallLogId;
    private Integer fallback;
    private String fallbackReason;
    private String inputHash;
    private String generationFingerprint;
}
