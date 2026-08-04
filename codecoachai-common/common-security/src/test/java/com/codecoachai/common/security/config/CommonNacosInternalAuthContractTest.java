package com.codecoachai.common.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class CommonNacosInternalAuthContractTest {

    @Test
    void commonConfigKeepsCallerKeyRingsReceiverSpecificAndDisablesSharedKeyFallback()
            throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Map<String, Object> commonConfig = new Yaml().load(Files.readString(
                repositoryRoot.resolve("docs/nacos/codecoachai-common-dev.yml")));
        Map<String, Object> codecoachai = mapping(commonConfig.get("codecoachai"));
        Map<String, Object> internal = mapping(codecoachai.get("internal"));
        Map<String, Object> auth = mapping(internal.get("auth"));

        assertFalse(
                auth.containsKey("caller-key-rings"),
                "The common property source must not replace receiver-specific caller ACLs");
        assertFalse((Boolean) auth.get("legacy-shared-secret-enabled"));
        assertTrue(((List<?>) auth.get("legacy-shared-secret-callers")).isEmpty());
    }

    @Test
    void consolidatedReceiversDeclareOnlyTheirExpectedCallers() throws IOException {
        Path repositoryRoot = findRepositoryRoot();

        Map<String, Object> coreAuth = internalAuth(repositoryRoot, "codecoachai-core-dev.yml");
        Map<String, Object> coreRings = mapping(coreAuth.get("caller-key-rings"));
        assertTrue(coreRings.containsKey("codecoachai-gateway"));
        assertTrue(coreRings.containsKey("codecoachai-ai"));
        assertTrue(coreRings.containsKey("codecoachai-search"));
        assertFalse((Boolean) coreAuth.get("legacy-shared-secret-enabled"));

        Map<String, Object> aiAuth = internalAuth(repositoryRoot, "codecoachai-ai-dev.yml");
        Map<String, Object> aiRings = mapping(aiAuth.get("caller-key-rings"));
        assertTrue(aiRings.containsKey("codecoachai-gateway"));
        assertTrue(aiRings.containsKey("codecoachai-core"));
        assertFalse((Boolean) aiAuth.get("legacy-shared-secret-enabled"));

        Map<String, Object> searchAuth = internalAuth(repositoryRoot, "codecoachai-search-dev.yml");
        assertTrue(mapping(searchAuth.get("caller-key-rings")).containsKey("codecoachai-gateway"));
        assertFalse((Boolean) searchAuth.get("legacy-shared-secret-enabled"));
    }

    @Test
    void defaultPublicNamespaceIsRepresentedByEmptyValues() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path envExample = repositoryRoot.resolve(".env.example");

        assertEquals("", envValue(envExample, "SPRING_CLOUD_NACOS_CONFIG_NAMESPACE"));
        assertEquals("", envValue(envExample, "SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE"));
        assertEquals("", envValue(envExample, "NACOS_NAMESPACE"));

        String gatewayConfig = Files.readString(repositoryRoot.resolve(
                "codecoachai-gateway/src/main/resources/application.yml"));
        assertEquals(
                2,
                gatewayConfig.lines()
                        .filter(line -> line.trim().equals("namespace: ${NACOS_NAMESPACE:}"))
                        .count());
    }

    private static Path findRepositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isRegularFile(candidate.resolve("docs/nacos/codecoachai-common-dev.yml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Cannot locate backend repository root");
    }

    private static Map<String, Object> internalAuth(Path repositoryRoot, String fileName) throws IOException {
        Map<String, Object> config = new Yaml().load(Files.readString(
                repositoryRoot.resolve("docs/nacos").resolve(fileName)));
        Map<String, Object> codecoachai = mapping(config.get("codecoachai"));
        return mapping(mapping(codecoachai.get("internal")).get("auth"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(Object value) {
        return (Map<String, Object>) value;
    }

    private static String envValue(Path envFile, String name) throws IOException {
        String prefix = name + "=";
        return Files.readAllLines(envFile).stream()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing environment variable: " + name));
    }
}
