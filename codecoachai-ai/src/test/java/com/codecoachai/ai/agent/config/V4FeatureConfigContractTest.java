package com.codecoachai.ai.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class V4FeatureConfigContractTest {

    @Test
    void acceptanceNacosConfigEnablesEveryFrontendExposedV4Capability() throws IOException {
        Path config = repositoryRoot().resolve("docs/nacos/codecoachai-ai-dev.yml");
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("v4-ai-nacos", new FileSystemResource(config))
                .forEach(environment.getPropertySources()::addFirst);

        assertEquals("true", environment.getProperty("codecoachai.v4.features.growth-enabled"));
        assertEquals("true", environment.getProperty("codecoachai.v4.features.knowledge-enabled"));
        assertEquals("true", environment.getProperty("codecoachai.v4.features.adaptive-plan-enabled"));
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
