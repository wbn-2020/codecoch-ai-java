package com.codecoachai.file.util;

import java.util.Locale;

public final class ResumeParseOperationContract {

    private ResumeParseOperationContract() {
    }

    public static State from(String parseStatus) {
        String status = parseStatus == null ? "" : parseStatus.trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "PENDING" -> new State("QUEUED", true, false, false);
            case "PARSING" -> new State("RUNNING", true, false, false);
            case "WAIT_CONFIRM" -> new State("AWAITING_CONFIRMATION", false, false, false);
            case "SUCCESS" -> new State("SUCCEEDED", false, false, false);
            case "FAILED" -> new State("FAILED", false, true, true);
            case "CANCELLED" -> new State("CANCELLED", false, false, false);
            default -> new State("UNKNOWN", false, false, false);
        };
    }

    public record State(
            String operationStatus,
            boolean cancellable,
            boolean retryable,
            boolean exposesError) {
    }
}
