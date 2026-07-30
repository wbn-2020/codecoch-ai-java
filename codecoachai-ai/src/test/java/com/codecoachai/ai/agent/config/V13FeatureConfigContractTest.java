package com.codecoachai.ai.agent.config;

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

class V13FeatureConfigContractTest {

    @Test
    void authoritativeNacosYamlEnablesV13AgentSkillGapContext() throws IOException {
        Path root = repositoryRoot();
        String yaml = Files.readString(
                root.resolve("docs/nacos/codecoachai-ai-dev.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("agent-skill-gap-context: true"));

        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("v13-ai-nacos", new FileSystemResource(
                        root.resolve("docs/nacos/codecoachai-ai-dev.yml")))
                .forEach(environment.getPropertySources()::addFirst);
        V13FeatureGate gate = Binder.get(environment)
                .bind("codecoachai.features.v13", Bindable.of(V13FeatureGate.class))
                .orElseThrow(() -> new IllegalStateException("V13 properties did not bind"));
        assertTrue(gate.isAgentSkillGapContext());
    }

    @Test
    void acceptanceProfileEnablesV13AgentSkillGapContext() throws IOException {
        Path yaml = Path.of("src/main/resources/application-acceptance.yml");
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("v13-ai-acceptance", new FileSystemResource(yaml))
                .forEach(environment.getPropertySources()::addFirst);
        V13FeatureGate gate = Binder.get(environment)
                .bind("codecoachai.features.v13", Bindable.of(V13FeatureGate.class))
                .orElseThrow(() -> new IllegalStateException("V13 acceptance properties did not bind"));

        assertTrue(gate.isAgentSkillGapContext());
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
