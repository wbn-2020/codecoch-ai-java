package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResumeNacosConfigContractTest {

    private static final String DOCS_CONFIG = "docs/nacos/codecoachai-core-dev.yml";
    private static final String APPLICATION_CONFIG = "codecoachai-core/src/main/resources/application.yml";
    private static final String STARTUP_ONLY_NOTICE =
            "Upload admission concurrency and wait settings apply at Core startup; restart is required after changes.";

    @Test
    void uploadAdmissionConfigsExplainThatChangesRequireRestart() throws IOException {
        Path root = repositoryRoot();

        assertTrue(Files.readString(root.resolve(DOCS_CONFIG)).contains(STARTUP_ONLY_NOTICE));
        assertTrue(Files.readString(root.resolve(APPLICATION_CONFIG)).contains(STARTUP_ONLY_NOTICE));
    }

    @Test
    void v12FeatureConfigDeclaresProfileFeedbackAndSampleThresholds() throws IOException {
        Path root = repositoryRoot();

        String content = Files.readString(root.resolve(DOCS_CONFIG));
        assertTrue(content.contains("v12:"),
                () -> DOCS_CONFIG + " must declare the v12 feature block");
        assertTrue(content.contains("evidence-profile-feedback: true"),
                () -> DOCS_CONFIG + " must enable v12 evidence profile feedback in the test env");
        assertTrue(content.contains("min-applications: 15"),
                () -> DOCS_CONFIG + " must declare the experiment min-applications threshold");
        assertTrue(content.contains("min-interviews: 3"),
                () -> DOCS_CONFIG + " must declare the experiment min-interviews threshold");
    }

    @Test
    void v13FeatureConfigDeclaresPositiveAbilityReinforcement() throws IOException {
        Path root = repositoryRoot();

        String content = Files.readString(root.resolve(DOCS_CONFIG));
        assertTrue(content.contains("v13:"),
                () -> DOCS_CONFIG + " must declare the v13 feature block");
        assertTrue(content.contains("positive-ability-reinforcement: true"),
                () -> DOCS_CONFIG + " must enable v13 positive ability reinforcement in the test env");
    }

    @Test
    void v8CampaignCapabilitiesAreEnabledForAcceptance() throws IOException {
        Path root = repositoryRoot();

        String content = Files.readString(root.resolve(DOCS_CONFIG));
        assertTrue(content.contains("campaign-cockpit: true"));
        assertTrue(content.contains("campaign-pulse: true"));
        assertTrue(content.contains("campaign-plan: true"));
        assertTrue(content.contains("campaign-portfolio: true"));
        assertTrue(content.contains("campaign-export: true"));
    }

    @Test
    void resumeDatasourceUsesContainerSafeEnvironmentPlaceholders() throws IOException {
        Path root = repositoryRoot();
        String expectedUrl =
                "url: jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:codecoachai_v1}";
        String expectedUsername = "username: ${MYSQL_USERNAME:root}";
        String expectedPassword = "password: ${MYSQL_PASSWORD}";

        String content = Files.readString(root.resolve(DOCS_CONFIG));
        assertTrue(content.contains(expectedUrl), () -> DOCS_CONFIG + " must use the Docker-safe datasource URL");
        assertTrue(content.contains(expectedUsername),
                () -> DOCS_CONFIG + " must obtain the datasource username from the environment");
        assertTrue(content.contains(expectedPassword),
                () -> DOCS_CONFIG + " must obtain the datasource password from the environment");
    }

    private static Path repositoryRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null) {
            Path root = Path.of(multiModuleRoot).toAbsolutePath().normalize();
            if (Files.exists(root.resolve(DOCS_CONFIG))) {
                return root;
            }
        }
        var applicationResource = ResumeNacosConfigContractTest.class.getClassLoader()
                .getResource("application.yml");
        if (applicationResource != null && "file".equalsIgnoreCase(applicationResource.getProtocol())) {
            try {
                Path current = Path.of(applicationResource.toURI()).toAbsolutePath();
                while (current != null && !Files.exists(current.resolve(DOCS_CONFIG))) {
                    current = current.getParent();
                }
                if (current != null) {
                    return current;
                }
            } catch (Exception ignored) {
            }
        }
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(DOCS_CONFIG))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }
        return current;
    }
}
