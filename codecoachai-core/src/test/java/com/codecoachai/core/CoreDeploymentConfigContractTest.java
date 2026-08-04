package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class CoreDeploymentConfigContractTest {

    @Test
    void interviewAiTimeoutMatchesFeignContextId() throws IOException {
        Map<String, Object> config = yaml("docs/nacos/codecoachai-core-dev.yml");
        Map<String, Object> spring = mapping(config.get("spring"));
        Map<String, Object> cloud = mapping(spring.get("cloud"));
        Map<String, Object> openfeign = mapping(cloud.get("openfeign"));
        Map<String, Object> client = mapping(openfeign.get("client"));
        Map<String, Object> clients = mapping(client.get("config"));
        Map<String, Object> interviewAi = mapping(clients.get("interviewAiFeignClient"));

        assertEquals(3000, interviewAi.get("connectTimeout"));
        assertEquals(40000, interviewAi.get("readTimeout"));
    }

    @Test
    void commonActuatorKeepsPrometheusAvailableAfterConsolidation() throws IOException {
        Map<String, Object> config = yaml("docs/nacos/codecoachai-common-dev.yml");
        Map<String, Object> management = mapping(config.get("management"));
        Map<String, Object> endpoints = mapping(management.get("endpoints"));
        Map<String, Object> web = mapping(endpoints.get("web"));
        Map<String, Object> exposure = mapping(web.get("exposure"));
        String includes = String.valueOf(exposure.get("include"));

        assertTrue(includes.contains("health"));
        assertTrue(includes.contains("info"));
        assertTrue(includes.contains("prometheus"));
    }

    @Test
    void commonConfigDoesNotForceContainerServicesToRegisterLoopback() throws IOException {
        Map<String, Object> config = yaml("docs/nacos/codecoachai-common-dev.yml");
        Map<String, Object> spring = mapping(config.get("spring"));
        Map<String, Object> cloud = mapping(spring.get("cloud"));

        assertFalse(cloud.containsKey("nacos"));
    }

    @Test
    void coreConfigDoesNotPretendToReplaceExplicitListenerConsumerGroups() throws IOException {
        Map<String, Object> config = yaml("docs/nacos/codecoachai-core-dev.yml");
        Map<String, Object> rocketmq = optionalMapping(config.get("rocketmq"));
        Map<String, Object> consumer = optionalMapping(rocketmq.get("consumer"));

        assertFalse(consumer.containsKey("group"));
    }

    private static Map<String, Object> yaml(String relativePath) throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        return new Yaml().load(Files.readString(repositoryRoot.resolve(relativePath)));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optionalMapping(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }
}
