package com.codecoachai.resume.careeroffer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codecoachai.common.core.domain.BaseEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("career_offer_version")
public class CareerOfferVersion extends BaseEntity {
    private Long userId;
    private Long offerId;
    private Integer versionNo;
    private Long campaignIdAtCreation;
    private String currency;
    private BigDecimal annualBaseSalary;
    private BigDecimal annualBonus;
    private BigDecimal signOnBonus;
    private BigDecimal annualEquityValue;
    private BigDecimal otherAnnualCompensation;
    private Integer paidLeaveDays;
    private String location;
    private String workMode;
    private LocalDate startDate;
    private LocalDateTime decisionDeadline;
    private String termsJson;
    private String note;
    private String idempotencyKeyHash;
    private String payloadHash;
}
