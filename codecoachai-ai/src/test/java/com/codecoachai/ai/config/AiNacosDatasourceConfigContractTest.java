package com.codecoachai.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class AiNacosDatasourceConfigContractTest {

    @Test
    void acceptanceNacosConfigUsesContainerReachableMysqlEndpoint() throws IOException {
        Path yaml = repositoryRoot().resolve("docs/nacos/codecoachai-ai-dev.yml");
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("ai-nacos-datasource", new FileSystemResource(yaml));

        assertEquals(
                "jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:codecoachai_v1}"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&useSSL=false&allowPublicKeyRetrieval=true",
                property(sources, "spring.datasource.url"));
        assertEquals("${MYSQL_USERNAME:root}", property(sources, "spring.datasource.username"));
    }

    private static Object property(List<PropertySource<?>> sources, String key) {
        return sources.stream()
                .map(source -> source.getProperty(key))
                .filter(value -> value != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing Nacos property: " + key));
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
