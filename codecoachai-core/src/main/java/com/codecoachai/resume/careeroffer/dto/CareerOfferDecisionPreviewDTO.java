package com.codecoachai.resume.careeroffer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.Data;

@Data
public class CareerOfferDecisionPreviewDTO {
    private String comparisonCurrency;
    private Map<String, BigDecimal> exchangeRates;
    private String exchangeRateSource;
    private LocalDate exchangeRateDate;
    private Map<String, BigDecimal> weights;
}
