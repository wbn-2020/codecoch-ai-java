package com.codecoachai.ai.agent.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class V9FeatureConfigContractTest {

    @Test
    void authoritativeNacosYamlEnablesApprovedV9EvidenceLearning() throws IOException {
        Path root = repositoryRoot();
        String yaml = Files.readString(
                root.resolve("docs/nacos/codecoachai-ai-dev.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("evidence-learning: true"));

        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("v9-ai-nacos", new FileSystemResource(
                        root.resolve("docs/nacos/codecoachai-ai-dev.yml")))
                .forEach(environment.getPropertySources()::addFirst);
        V9FeatureGate gate = Binder.get(environment)
                .bind("codecoachai.features.v9", Bindable.of(V9FeatureGate.class))
                .orElseThrow(() -> new IllegalStateException("V9 properties did not bind"));
        assertDoesNotThrow(gate::requireEvidenceLearning);
    }

    @Test
    void acceptanceProfileEnablesV9EvidenceLearning() throws IOException {
        Path yaml = Path.of("src/main/resources/application-acceptance.yml");
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("v9-ai-acceptance", new FileSystemResource(yaml))
                .forEach(environment.getPropertySources()::addFirst);
        V9FeatureGate gate = Binder.get(environment)
                .bind("codecoachai.features.v9", Bindable.of(V9FeatureGate.class))
                .orElseThrow(() -> new IllegalStateException("V9 acceptance properties did not bind"));

        assertDoesNotThrow(gate::requireEvidenceLearning);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null
                && !Files.exists(current.resolve("docs/nacos/codecoachai-ai-dev.yml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate repository root");
        }
        return current;
    }
}
