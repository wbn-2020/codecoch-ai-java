package com.codecoachai.search.feign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

class SearchCoreFeignContractTest {

    @Test
    void searchSyncClientsTargetTheConsolidatedCoreService() {
        assertEquals("codecoachai-core", feignName(ResumeFeignClient.class));
        assertEquals("codecoachai-core", feignName(QuestionFeignClient.class));
        assertEquals("codecoachai-core", feignName(InterviewFeignClient.class));
    }

    private String feignName(Class<?> clientType) {
        return clientType.getAnnotation(FeignClient.class).name();
    }
}
