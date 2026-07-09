package com.codecoachai.ai.domain.enums;

public enum AiResultSourceEnum {

    LLM("\u771f\u5b9e\u6a21\u578b"),
    MOCK("\u6a21\u62df\u6570\u636e"),
    FALLBACK("\u964d\u7ea7\u515c\u5e95"),
    DEGRADED("\u964d\u7ea7\u7ed3\u679c");

    private final String label;

    AiResultSourceEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static AiResultSourceEnum normalize(String value) {
        if (value == null || value.isBlank()) {
            return LLM;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("MOCK")) {
            return MOCK;
        }
        if (normalized.contains("DEGRADED")) {
            return DEGRADED;
        }
        if (normalized.contains("FALLBACK") || normalized.contains("->")) {
            return FALLBACK;
        }
        try {
            return AiResultSourceEnum.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return LLM;
        }
    }
}
