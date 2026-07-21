package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.StandardEnvironment;

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

    @Test
    void authoritativeNacosYamlBindsEveryResumeV7Capability() throws IOException {
        V7FeatureGate gate = bindNacosGate(
                repositoryRoot().resolve("docs/nacos/codecoachai-resume-dev.yml"));

        assertDoesNotThrow(gate::requireCampaignWorkspace);
        assertDoesNotThrow(gate::requireRealInterview);
        assertDoesNotThrow(gate::requireOffer);
        assertDoesNotThrow(gate::requireContactActivity);
        assertDoesNotThrow(gate::requireResearch);
        assertEquals(
                List.of("REAL_INTERVIEW", "OFFER", "CONTACT_ACTIVITY", "RESEARCH"),
                gate.enabledCapabilities());
    }

    private static V7FeatureGate bindNacosGate(Path path) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("v7-resume-nacos", new FileSystemResource(path))
                .forEach(environment.getPropertySources()::addFirst);
        return Binder.get(environment)
                .bind("codecoachai.features.v7", Bindable.of(V7FeatureGate.class))
                .orElseThrow(() -> new IllegalStateException("Resume V7 Nacos properties did not bind"));
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
