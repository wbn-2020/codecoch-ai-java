package com.codecoachai.ai.agent.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V7FeatureConfigContractTest {

    @Test
    void localDefaultsDoNotShadowNacosV7Flags() throws IOException {
        String applicationYaml = Files.readString(
                Path.of("src/main/resources/application.yml"),
                StandardCharsets.UTF_8);
        String nacosYaml = Files.readString(
                repositoryRoot().resolve("docs/nacos/codecoachai-ai-dev.yml"),
                StandardCharsets.UTF_8);

        assertFalse(applicationYaml.contains("CODECOACHAI_V7_EXTERNAL_PLAN_SOURCE_ENABLED"));
        assertFalse(applicationYaml.contains("CODECOACHAI_V7_CAMPAIGN_REVIEW_ENABLED"));
        assertTrue(nacosYaml.contains("external-plan-source: true"));
        assertTrue(nacosYaml.contains("campaign-review: true"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("docs/nacos/codecoachai-ai-dev.yml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }
        return current;
    }
}
