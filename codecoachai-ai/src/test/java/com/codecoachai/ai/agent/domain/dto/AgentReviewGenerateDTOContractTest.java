package com.codecoachai.ai.agent.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AgentReviewGenerateDTOContractTest {

    @Test
    void externalReviewRequestCannotControlReviewIdentityOrIdempotency() {
        Set<String> fields = Arrays.stream(AgentReviewGenerateDTO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("targetJobId", "date"), fields);
    }
}
