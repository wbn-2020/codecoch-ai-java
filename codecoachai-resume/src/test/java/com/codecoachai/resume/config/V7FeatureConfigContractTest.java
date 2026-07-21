package com.codecoachai.resume.config;

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
                repositoryRoot().resolve("docs/nacos/codecoachai-resume-dev.yml"),
                StandardCharsets.UTF_8);

        assertFalse(applicationYaml.contains("CODECOACHAI_V7_"));
        assertTrue(nacosYaml.contains("campaign-workspace: true"));
        assertTrue(nacosYaml.contains("real-interview: true"));
        assertTrue(nacosYaml.contains("offer: true"));
        assertTrue(nacosYaml.contains("contact-activity: true"));
        assertTrue(nacosYaml.contains("research: true"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("docs/nacos/codecoachai-resume-dev.yml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }
        return current;
    }
}
