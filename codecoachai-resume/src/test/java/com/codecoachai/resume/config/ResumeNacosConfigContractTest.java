package com.codecoachai.resume.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResumeNacosConfigContractTest {

    private static final String DOCS_CONFIG = "docs/nacos/codecoachai-resume-dev.yml";
    private static final String LEGACY_CONFIG = "config/nacos/codecoachai-resume-dev.yml";
    private static final String APPLICATION_CONFIG = "codecoachai-resume/src/main/resources/application.yml";
    private static final String STARTUP_ONLY_NOTICE =
            "Upload admission concurrency and wait settings apply at resume service startup; restart is required after changes.";

    @Test
    void legacyResumeConfigMirrorsAuthoritativeDocsConfig() throws IOException {
        Path root = repositoryRoot();
        Path docsConfig = root.resolve(DOCS_CONFIG);
        Path legacyConfig = root.resolve(LEGACY_CONFIG);

        assertTrue(Files.isRegularFile(legacyConfig),
                () -> LEGACY_CONFIG + " must mirror the authoritative " + DOCS_CONFIG);
        assertEquals(normalized(Files.readString(docsConfig)), normalized(Files.readString(legacyConfig)));
    }

    @Test
    void uploadAdmissionConfigsExplainThatChangesRequireRestart() throws IOException {
        Path root = repositoryRoot();

        assertTrue(Files.readString(root.resolve(DOCS_CONFIG)).contains(STARTUP_ONLY_NOTICE));
        assertTrue(Files.readString(root.resolve(LEGACY_CONFIG)).contains(STARTUP_ONLY_NOTICE));
        assertTrue(Files.readString(root.resolve(APPLICATION_CONFIG)).contains(STARTUP_ONLY_NOTICE));
    }

    @Test
    void resumeDatasourceUsesContainerSafeEnvironmentPlaceholders() throws IOException {
        Path root = repositoryRoot();
        String expectedUrl =
                "url: jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:codecoachai_v1}";
        String expectedUsername = "username: ${MYSQL_USERNAME:root}";
        String expectedPassword = "password: ${MYSQL_PASSWORD}";

        for (String configPath : new String[] {DOCS_CONFIG, LEGACY_CONFIG}) {
            String content = Files.readString(root.resolve(configPath));
            assertTrue(content.contains(expectedUrl), () -> configPath + " must use the Docker-safe datasource URL");
            assertTrue(content.contains(expectedUsername),
                    () -> configPath + " must obtain the datasource username from the environment");
            assertTrue(content.contains(expectedPassword),
                    () -> configPath + " must obtain the datasource password from the environment");
        }
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

    private static String normalized(String content) {
        return content.replace("\r\n", "\n").stripTrailing();
    }
}
