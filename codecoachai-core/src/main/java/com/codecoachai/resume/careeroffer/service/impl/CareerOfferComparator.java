package com.codecoachai.resume.careeroffer.service.impl;

import com.codecoachai.resume.careeroffer.dto.CareerOfferDecisionPreviewDTO;
import com.codecoachai.resume.careeroffer.entity.CareerOfferVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class CareerOfferComparator {

    private static final List<String> COMPONENTS = List.of(
            "annualBaseSalary", "annualBonus", "signOnBonus", "annualEquityValue", "otherAnnualCompensation");
    private static final Map<String, BigDecimal> DEFAULT_WEIGHTS = Map.of(
            "annualBaseSalary", new BigDecimal("40"),
            "annualBonus", new BigDecimal("15"),
            "signOnBonus", new BigDecimal("5"),
            "annualEquityValue", new BigDecimal("15"),
            "otherAnnualCompensation", new BigDecimal("5"));

    private CareerOfferComparator() {
    }

    public static Comparison compare(List<CareerOfferVersion> versions, CareerOfferDecisionPreviewDTO request) {
        String comparisonCurrency = normalizeCurrency(request == null ? null : request.getComparisonCurrency());
        Map<String, BigDecimal> rates = normalizedRates(request == null ? null : request.getExchangeRates());
        boolean sameCurrency = versions.stream().map(CareerOfferVersion::getCurrency)
                .filter(Objects::nonNull).map(CareerOfferComparator::normalizeCurrency).distinct().count() <= 1;
        if (comparisonCurrency == null && !versions.isEmpty()) {
            comparisonCurrency = normalizeCurrency(versions.get(0).getCurrency());
        }
        final String targetCurrency = comparisonCurrency;
        List<String> limitations = new ArrayList<>();
        if (!sameCurrency) {
            if (targetCurrency == null || versions.stream().anyMatch(v ->
                    v.getCurrency() == null || (!v.getCurrency().equalsIgnoreCase(targetCurrency)
                            && !rates.containsKey(normalizeCurrency(v.getCurrency()))))) {
                limitations.add("CROSS_CURRENCY_RATE_REQUIRED");
            }
        }
        boolean comparable = limitations.isEmpty();
        Map<String, BigDecimal> weights = weights(request == null ? null : request.getWeights());
        BigDecimal totalWeight = weights.values().stream()
                .filter(Objects::nonNull)
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> maxima = new LinkedHashMap<>();
        for (String component : COMPONENTS) {
            maxima.put(component, versions.stream()
                    .map(v -> converted(componentValue(v, component), v.getCurrency(), targetCurrency, rates))
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null));
        }
        List<Item> items = new ArrayList<>();
        for (CareerOfferVersion version : versions) {
            List<String> missing = new ArrayList<>();
            BigDecimal weighted = BigDecimal.ZERO;
            for (String component : COMPONENTS) {
                BigDecimal value = converted(componentValue(version, component), version.getCurrency(),
                        targetCurrency, rates);
                if (value == null || maxima.get(component) == null || maxima.get(component).signum() == 0) {
                    missing.add(component);
                    continue;
                }
                BigDecimal weight = weights.get(component);
                weighted = weighted.add(value.divide(maxima.get(component), 12, RoundingMode.HALF_UP)
                        .multiply(weight));
            }
            BigDecimal score = totalWeight.signum() == 0
                    ? null : weighted.divide(totalWeight, 8, RoundingMode.HALF_UP);
            if (!comparable) {
                score = null;
            }
            BigDecimal annualValue = annualValue(version, targetCurrency, rates);
            items.add(new Item(version, annualValue, score, missing));
        }
        items.sort(Comparator.comparing(Item::score, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Item::annualValue, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(item -> item.version().getOfferId()));
        return new Comparison(targetCurrency, comparable, limitations, weights, items);
    }

    private static BigDecimal annualValue(CareerOfferVersion version, String currency,
                                          Map<String, BigDecimal> rates) {
        BigDecimal total = null;
        for (String component : COMPONENTS) {
            BigDecimal value = componentValue(version, component);
            if (value != null) {
                total = total == null ? value : total.add(value);
            }
        }
        return converted(total, version.getCurrency(), currency, rates);
    }

    private static BigDecimal componentValue(CareerOfferVersion version, String component) {
        return switch (component) {
            case "annualBaseSalary" -> version.getAnnualBaseSalary();
            case "annualBonus" -> version.getAnnualBonus();
            case "signOnBonus" -> version.getSignOnBonus();
            case "annualEquityValue" -> version.getAnnualEquityValue();
            case "otherAnnualCompensation" -> version.getOtherAnnualCompensation();
            default -> null;
        };
    }

    private static BigDecimal converted(BigDecimal value, String currency, String target,
                                       Map<String, BigDecimal> rates) {
        if (value == null || currency == null || target == null) {
            return null;
        }
        String source = normalizeCurrency(currency);
        if (source.equals(target)) {
            return value;
        }
        BigDecimal rate = rates.get(source);
        return rate == null ? null : value.multiply(rate);
    }

    private static Map<String, BigDecimal> normalizedRates(Map<String, BigDecimal> input) {
        Map<String, BigDecimal> result = new TreeMap<>();
        if (input != null) {
            input.forEach((key, value) -> {
                if (key != null && value != null && value.signum() > 0) {
                    result.put(normalizeCurrency(key), value);
                }
            });
        }
        return result;
    }

    private static Map<String, BigDecimal> weights(Map<String, BigDecimal> input) {
        Map<String, BigDecimal> result = new LinkedHashMap<>(DEFAULT_WEIGHTS);
        if (input != null) {
            input.forEach((key, value) -> {
                if (COMPONENTS.contains(key) && value != null && value.signum() >= 0) {
                    result.put(key, value);
                }
            });
        }
        return result;
    }

    public static String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Comparison(String comparisonCurrency, boolean comparable, List<String> limitations,
                             Map<String, BigDecimal> weights, List<Item> items) {
    }

    public record Item(CareerOfferVersion version, BigDecimal annualValue, BigDecimal score,
                       List<String> missingItems) {
    }
}
