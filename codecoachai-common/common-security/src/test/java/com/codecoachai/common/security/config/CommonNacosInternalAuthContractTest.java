package com.codecoachai.common.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class CommonNacosInternalAuthContractTest {

    @Test
    void commonConfigDoesNotMaskReceiverSpecificCallerKeyRings() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Map<String, Object> commonConfig = new Yaml().load(Files.readString(
                repositoryRoot.resolve("docs/nacos/codecoachai-common-dev.yml")));
        Map<String, Object> codecoachai = mapping(commonConfig.get("codecoachai"));
        Map<String, Object> internal = mapping(codecoachai.get("internal"));
        Map<String, Object> auth = mapping(internal.get("auth"));

        assertFalse(
                auth.containsKey("caller-key-rings"),
                "The common property source must not replace receiver-specific caller ACLs");
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
