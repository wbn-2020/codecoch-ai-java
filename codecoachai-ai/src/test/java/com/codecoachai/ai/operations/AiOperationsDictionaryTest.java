package com.codecoachai.ai.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiOperationsDictionaryTest {

    @Test
    void mapsKnownSceneToOperationalChineseLabel() {
        AiOperationsDictionary.SceneDescription description =
                AiOperationsDictionary.describeScene("INTERVIEW_ANSWER_EVALUATE");

        assertEquals("面试回答评估", description.label());
        assertEquals("模拟面试", description.category());
        assertTrue(description.registered());
    }

    @Test
    void keepsUnknownSceneCodeVisibleWithoutPretendingItIsRegistered() {
        AiOperationsDictionary.SceneDescription description =
                AiOperationsDictionary.describeScene("NEW_BILLING_FLOW");

        assertEquals("NEW_BILLING_FLOW", description.code());
        assertEquals("未登记场景", description.label());
        assertFalse(description.registered());
    }

    @Test
    void mapsTimeoutAndKeepsOperatorGuidanceSeparateFromTechnicalText() {
        AiOperationsDictionary.FailureDescription description =
                AiOperationsDictionary.describeFailure(
                        "errorType=TIMEOUT; errorRef=abc; summary=provider timed out", 0, 0);

        assertEquals("TIMEOUT", description.code());
        assertEquals("调用超时", description.label());
        assertTrue(description.operatorSuggestion().contains("稍后重试"));
    }

    @Test
    void specializesHttpAuthenticationAndRateLimitFailures() {
        AiOperationsDictionary.FailureDescription unauthorized =
                AiOperationsDictionary.describeFailure(
                        "errorType=HTTP_ERROR; httpStatus=401; errorRef=abc", 0, 0);
        AiOperationsDictionary.FailureDescription rateLimited =
                AiOperationsDictionary.describeFailure(
                        "errorType=HTTP_ERROR; httpStatus=429; errorRef=def", 0, 0);

        assertEquals("供应商鉴权失败", unauthorized.label());
        assertEquals(401, unauthorized.httpStatus());
        assertEquals("供应商限流或额度不足", rateLimited.label());
        assertEquals(429, rateLimited.httpStatus());
    }
}
