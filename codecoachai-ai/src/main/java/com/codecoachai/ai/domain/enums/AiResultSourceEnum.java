package com.codecoachai.ai.domain.enums;

public enum AiResultSourceEnum {

    LLM("真实模型"),
    MOCK("模拟数据"),
    FALLBACK("降级兜底");

    private final String label;

    AiResultSourceEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
