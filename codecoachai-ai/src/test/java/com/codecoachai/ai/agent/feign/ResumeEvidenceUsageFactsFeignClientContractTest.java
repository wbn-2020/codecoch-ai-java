package com.codecoachai.ai.agent.feign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

class ResumeEvidenceUsageFactsFeignClientContractTest {

    @Test
    void factsClientAcceptsOnlyServerUserAndBusinessIds() throws Exception {
        Method method = ResumeEvidenceUsageFactsFeignClient.class.getMethod(
                "getFacts", Long.class, Long.class, Long.class, Long.class, LocalDateTime.class);

        assertEquals(5, method.getParameterCount());
        assertTrue(Arrays.stream(method.getParameterAnnotations())
                .flatMap(Arrays::stream)
                .filter(RequestParam.class::isInstance)
                .map(RequestParam.class::cast)
                .allMatch(annotation -> Arrays.asList(
                        "campaignId", "applicationId", "usageId", "dataCutoffAt")
                        .contains(annotation.value().isEmpty()
                                ? annotation.name() : annotation.value())));
        assertFalse(Arrays.stream(method.getParameterTypes())
                .anyMatch(type -> type == String.class
                        || type == ResumeEvidenceUsageFactsVO.class));
    }
}
