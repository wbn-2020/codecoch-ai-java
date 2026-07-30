package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.resume.feign.AiFeignClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

class LongRunningFeignClientConfigContractTest {

    private static final String COMMON_NACOS_CONFIG = "docs/nacos/codecoachai-common-dev.yml";

    @Test
    void resumeAiClientUsesTheNamedLongRunningConfiguration() {
        FeignClient annotation = AiFeignClient.class.getAnnotation(FeignClient.class);

        assertEquals("codecoachai-ai", annotation.name());
        assertEquals("resumeAiFeignClient", annotation.contextId());
    }

    @Test
    void commonNacosConfigExtendsOnlyLongRunningResumeAiPaths() throws IOException {
        String config = Files.readString(repositoryRoot().resolve(COMMON_NACOS_CONFIG));

        assertTrue(config.contains("resumeAiFeignClient:"));
        assertTrue(config.contains("taskResumeFeignClient:"));
        assertTrue(config.contains("readTimeout: 120000"));
        assertTrue(config.contains("default:\n            connectTimeout: 3000\n            readTimeout: 5000"));
    }

    private static Path repositoryRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null) {
            Path root = Path.of(multiModuleRoot).toAbsolutePath().normalize();
            if (Files.exists(root.resolve(COMMON_NACOS_CONFIG))) {
                return root;
            }
        }
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(COMMON_NACOS_CONFIG))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }
        return current;
    }
}
