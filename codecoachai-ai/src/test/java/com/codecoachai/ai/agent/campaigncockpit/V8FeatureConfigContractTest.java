package com.codecoachai.ai.agent.campaigncockpit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class V8FeatureConfigContractTest {

    @Test
    void acceptanceNacosConfigEnablesEveryCampaignCapability() throws IOException {
        Path config = repositoryRoot().resolve("docs/nacos/codecoachai-ai-dev.yml");
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("v8-ai-nacos", new FileSystemResource(config))
                .forEach(environment.getPropertySources()::addFirst);
        V8FeatureGate gate = Binder.get(environment)
                .bind("codecoachai.features.v8", Bindable.of(V8FeatureGate.class))
                .orElseThrow(() -> new IllegalStateException("AI V8 Nacos properties did not bind"));

        assertDoesNotThrow(gate::requireCampaignCockpit);
        assertDoesNotThrow(gate::requireCampaignPulse);
        assertDoesNotThrow(gate::requireCampaignPlan);
        assertDoesNotThrow(gate::requireCampaignPortfolio);
        assertDoesNotThrow(gate::requireCampaignExport);
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
