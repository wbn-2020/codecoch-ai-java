package com.codecoachai.resume.careeroffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionPreviewDTO;
import com.codecoachai.resume.careeroffer.entity.CareerOfferVersion;
import com.codecoachai.resume.careeroffer.service.impl.CareerOfferComparator;
import com.codecoachai.resume.careeroffer.service.impl.CareerOfferComparator.Comparison;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CareerOfferComparatorTest {

    @Test
    void comparesSameCurrencyDeterministicallyWithoutConvertingNullToZero() {
        CareerOfferVersion first = version(11L, 101L, "CNY", "100000.10", null);
        CareerOfferVersion second = version(12L, 102L, "CNY", "100000.09", "1000.01");

        Comparison result = CareerOfferComparator.compare(List.of(first, second),
                new CareerOfferDecisionPreviewDTO());

        assertTrue(result.comparable());
        assertEquals("CNY", result.comparisonCurrency());
        assertEquals(12L, result.items().get(0).version().getOfferId());
        assertTrue(result.items().get(0).annualValue().compareTo(new BigDecimal("101000.10")) == 0);
        assertTrue(result.items().get(1).missingItems().contains("annualBonus"));
    }

    @Test
    void blocksCrossCurrencyScoreWhenNoUserRateWasProvided() {
        CareerOfferVersion cny = version(11L, 101L, "CNY", "300000", null);
        CareerOfferVersion usd = version(12L, 102L, "USD", "100000", null);

        Comparison result = CareerOfferComparator.compare(List.of(cny, usd),
                new CareerOfferDecisionPreviewDTO());

        assertFalse(result.comparable());
        assertTrue(result.limitations().contains("CROSS_CURRENCY_RATE_REQUIRED"));
        assertNull(result.items().get(0).score());
        assertNull(result.items().get(1).score());
    }

    @Test
    void usesExplicitPositiveRatesForCrossCurrencyComparison() {
        CareerOfferVersion cny = version(11L, 101L, "CNY", "700000", null);
        CareerOfferVersion usd = version(12L, 102L, "USD", "100000", null);
        CareerOfferDecisionPreviewDTO request = new CareerOfferDecisionPreviewDTO();
        request.setComparisonCurrency("CNY");
        request.setExchangeRates(Map.of("USD", new BigDecimal("7.10")));

        Comparison result = CareerOfferComparator.compare(List.of(cny, usd), request);

        assertTrue(result.comparable());
        assertEquals(12L, result.items().get(0).version().getOfferId());
        assertTrue(result.items().get(0).annualValue().compareTo(new BigDecimal("710000.00")) == 0);
    }

    private static CareerOfferVersion version(Long offerId, Long versionId, String currency,
                                              String base, String bonus) {
        CareerOfferVersion version = new CareerOfferVersion();
        version.setId(versionId);
        version.setOfferId(offerId);
        version.setCurrency(currency);
        version.setAnnualBaseSalary(base == null ? null : new BigDecimal(base));
        version.setAnnualBonus(bonus == null ? null : new BigDecimal(bonus));
        return version;
    }
}
