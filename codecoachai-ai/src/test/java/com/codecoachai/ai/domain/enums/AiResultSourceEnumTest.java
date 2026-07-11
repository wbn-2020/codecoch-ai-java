package com.codecoachai.ai.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AiResultSourceEnumTest {

    @Test
    void normalizeRecognizesRuleSources() {
        assertEquals(AiResultSourceEnum.RULE, AiResultSourceEnum.normalize("RULE"));
        assertEquals(AiResultSourceEnum.RULE, AiResultSourceEnum.normalize("deterministic_rule"));
    }

    @Test
    void normalizeDoesNotMisclassifyUnknownSourcesAsLlm() {
        assertEquals(AiResultSourceEnum.UNKNOWN, AiResultSourceEnum.normalize(null));
        assertEquals(AiResultSourceEnum.UNKNOWN, AiResultSourceEnum.normalize(" "));
        assertEquals(AiResultSourceEnum.UNKNOWN, AiResultSourceEnum.normalize("legacy-unmapped-source"));
    }
}
