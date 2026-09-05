package com.codecoachai.resume.careeroffer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CareerOfferVersionCreateDTO {
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
}
