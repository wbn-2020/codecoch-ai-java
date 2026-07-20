package com.codecoachai.ai.agent.domain.enums;

import java.util.Locale;

public enum AgentPlanSourceType {
    DAILY_REVIEW,
    WEEKLY_REPORT,
    INTERVIEW_PREPARATION;

    public static AgentPlanSourceType parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("sourceType is required");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
