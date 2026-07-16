package com.codecoachai.resume.feign;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.common.feign.multipart.StreamingMultipartFeignConfiguration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

class FileFeignClientMultipartContractTest {

    @Test
    void fileClientUsesDedicatedStreamingMultipartConfiguration() {
        FeignClient annotation = FileFeignClient.class.getAnnotation(FeignClient.class);

        assertTrue(Arrays.asList(annotation.configuration())
                .contains(StreamingMultipartFeignConfiguration.class));
    }
}
