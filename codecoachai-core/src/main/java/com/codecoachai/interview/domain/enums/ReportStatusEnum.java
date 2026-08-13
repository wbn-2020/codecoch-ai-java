package com.codecoachai.interview.domain.enums;

import java.util.Locale;
import java.util.Set;

public enum ReportStatusEnum {
    NOT_GENERATED,
    GENERATING,
    GENERATED,
    UNSCORABLE,
    FAILED;

    private static final Set<String> COMPARISON_READY_STATUSES =
            Set.of(GENERATED.name(), "COMPLETED", "SUCCESS");

    public static boolean isComparisonReady(String status) {
        return status != null
                && COMPARISON_READY_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }
}
