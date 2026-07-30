package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class PromptContractConfigContractTest {

    @Test
    void authoritativeNacosYamlEnablesFailFastPromptContractCheck() throws IOException {
        Path yaml = repositoryRoot().resolve("docs/nacos/codecoachai-ai-dev.yml");
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("prompt-contract-nacos", new FileSystemResource(yaml))
                .forEach(environment.getPropertySources()::addFirst);

        PromptContractProperties properties = Binder.get(environment)
                .bind("codecoachai.ai.prompt-contract", Bindable.of(PromptContractProperties.class))
                .orElseThrow(() -> new IllegalStateException("Prompt contract properties did not bind"));

        assertTrue(properties.isStartupCheckEnabled());
        assertTrue(properties.isFailFast());
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
