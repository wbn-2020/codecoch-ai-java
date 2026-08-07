package com.codecoachai.resume.service.support;

import java.util.Locale;
import java.util.Set;

public final class JobApplicationLifecyclePolicy {

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("ACCEPTED", "DECLINED", "REJECTED", "WITHDRAWN", "CLOSED");

    private JobApplicationLifecyclePolicy() {
    }

    public static String normalize(String status) {
        return status == null || status.isBlank()
                ? "DRAFT"
                : status.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isTerminal(String status) {
        return TERMINAL_STATUSES.contains(normalize(status));
    }

    public static Set<String> terminalStatuses() {
        return TERMINAL_STATUSES;
    }
}
