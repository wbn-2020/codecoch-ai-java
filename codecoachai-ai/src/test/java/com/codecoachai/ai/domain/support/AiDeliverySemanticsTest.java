package com.codecoachai.ai.domain.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.codecoachai.ai.domain.enums.AiResultSourceEnum;
import org.junit.jupiter.api.Test;

class AiDeliverySemanticsTest {

    @Test
    void mockEvidenceOverridesIncorrectLegacyPrimaryClassification() {
        AiDeliverySemantics.Outcome outcome = AiDeliverySemantics.resolve(
                AiDeliverySemantics.PRIMARY_MODEL,
                AiDeliverySemantics.COMPLETE,
                AiResultSourceEnum.LLM.name(),
                "deepseek-chat(mock)",
                "deepseek",
                "{\"mockMode\":true}",
                null,
                1);

        assertEquals(AiDeliverySemantics.MOCK, outcome.executionSource());
        assertEquals(AiDeliverySemantics.DEGRADED, outcome.deliveryQuality());
        assertEquals(AiResultSourceEnum.MOCK, outcome.legacyResultSource());
        assertFalse(outcome.fallback());
    }

    @Test
    void legacyFallbackAndRuleFieldsMapToTheCanonicalAxes() {
        AiDeliverySemantics.Outcome fallback = AiDeliverySemantics.fromLegacy(
                AiResultSourceEnum.FALLBACK.name(),
                "qwen-plus",
                "deepseek -> dashscope",
                null,
                null,
                1);
        AiDeliverySemantics.Outcome rule = AiDeliverySemantics.fromLegacy(
                AiResultSourceEnum.RULE.name(),
                "rule-engine",
                "rule",
                null,
                null,
                1);

        assertEquals(AiDeliverySemantics.FALLBACK_MODEL, fallback.executionSource());
        assertEquals(AiDeliverySemantics.COMPLETE, fallback.deliveryQuality());
        assertEquals(AiDeliverySemantics.RULE_ENGINE, rule.executionSource());
        assertEquals(AiDeliverySemantics.DEGRADED, rule.deliveryQuality());
    }

    @Test
    void failedCallCannotRemainComplete() {
        AiDeliverySemantics.Outcome outcome = AiDeliverySemantics.resolve(
                AiDeliverySemantics.PRIMARY_MODEL,
                AiDeliverySemantics.COMPLETE,
                AiResultSourceEnum.LLM.name(),
                "deepseek-chat",
                "deepseek",
                null,
                null,
                0);

        assertEquals(AiDeliverySemantics.PRIMARY_MODEL, outcome.executionSource());
        assertEquals(AiDeliverySemantics.FAILED, outcome.deliveryQuality());
    }
}
