package com.codecoachai.interview.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class InterviewReportAsyncServiceTransactionBoundaryTest {

    @Test
    void asyncOrchestratorDoesNotOwnTransactionalCompletionMethods() {
        List<String> transactionalMethods = Arrays.stream(InterviewReportAsyncService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .map(Method::getName)
                .toList();

        assertTrue(transactionalMethods.isEmpty(),
                () -> "InterviewReportAsyncService should delegate transactional completion to another Spring bean, "
                        + "but found @Transactional methods: " + transactionalMethods);
    }

    @Test
    void transactionalCompletionBeanOwnsRequiredTransactionBoundaries() throws Exception {
        Class<?> completionBean = Class.forName(
                "com.codecoachai.interview.service.impl.InterviewReportTransactionService");

        Method completeSuccess = Arrays.stream(completionBean.getDeclaredMethods())
                .filter(method -> method.getName().equals("completeReportSuccess"))
                .findFirst()
                .orElseThrow();
        Method completeFailed = Arrays.stream(completionBean.getDeclaredMethods())
                .filter(method -> method.getName().equals("completeReportFailed"))
                .findFirst()
                .orElseThrow();

        Transactional successTransactional = completeSuccess.getAnnotation(Transactional.class);
        Transactional failedTransactional = completeFailed.getAnnotation(Transactional.class);

        assertNotNull(successTransactional, "completeReportSuccess should stay transactional");
        assertNotNull(failedTransactional, "completeReportFailed should stay transactional");
        assertEquals(Propagation.REQUIRES_NEW, failedTransactional.propagation(),
                "completeReportFailed should own a REQUIRES_NEW transaction boundary");
    }

    @Test
    void interviewReportPayloadCarriesReportVersionIdentity() throws Exception {
        Class<?> payloadClass = Class.forName(
                "com.codecoachai.common.mq.payload.InterviewReportPayload");

        assertDoesNotThrow(() -> payloadClass.getDeclaredField("reportId"),
                "Interview report MQ payload should carry reportId so stale tasks cannot overwrite newer versions");
        assertDoesNotThrow(() -> payloadClass.getDeclaredField("generationToken"),
                "Interview report MQ payload should carry generationToken so stale retries cannot overwrite newer attempts");
    }
}
