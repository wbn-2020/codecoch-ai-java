package com.codecoachai.common.core.enums;

import java.util.Locale;

/**
 * Cross-module business execution state.
 *
 * <p>Existing tables keep their historical status values for compatibility.
 * This enum is the read/write contract exposed by newer APIs.</p>
 */
public enum AsyncExecutionStatus {
    PENDING(false, false),
    RUNNING(false, false),
    SUCCEEDED(true, true),
    SUCCEEDED_DEGRADED(true, true),
    FAILED_RETRYABLE(true, false),
    FAILED_FINAL(true, false),
    CANCELLED(true, false);

    private final boolean terminal;
    private final boolean successful;

    AsyncExecutionStatus(boolean terminal, boolean successful) {
        this.terminal = terminal;
        this.successful = successful;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public static AsyncExecutionStatus fromLegacy(String status,
                                                  boolean consumableResult,
                                                  boolean degraded) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING", "QUEUED" -> PENDING;
            case "RUNNING", "PROCESSING" -> RUNNING;
            case "SUCCESS", "SUCCEEDED", "COMPLETED" ->
                    consumableResult
                            ? (degraded ? SUCCEEDED_DEGRADED : SUCCEEDED)
                            : FAILED_FINAL;
            case "CANCELED", "CANCELLED" -> CANCELLED;
            case "DEAD", "DEAD_LETTER", "FAILED_FINAL" -> FAILED_FINAL;
            case "FAILED", "ERROR", "FAIL", "FAILED_RETRYABLE" -> FAILED_RETRYABLE;
            default -> FAILED_RETRYABLE;
        };
    }

    public static boolean isTerminal(String status) {
        return fromLegacy(status, true, false).isTerminal()
                && !"PENDING".equalsIgnoreCase(status)
                && !"RUNNING".equalsIgnoreCase(status);
    }
}
