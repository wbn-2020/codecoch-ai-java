package com.codecoachai.common.feign.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class OpenFeignNacosConfigContractTest {

    @Test
    void commonConfigBindsDefaultOpenFeignTimeoutsFromCurrentPrefix() throws IOException {
        StandardEnvironment environment = environment("docs/nacos/codecoachai-common-dev.yml");
        FeignClientProperties properties = bind(environment);

        FeignClientProperties.FeignClientConfiguration defaults =
                properties.getConfig().get("default");
        assertEquals(3000, defaults.getConnectTimeout());
        assertEquals(5000, defaults.getReadTimeout());
        assertFalse(environment.containsProperty("feign.client.config.default.connectTimeout"));
    }

    @Test
    void interviewConfigBindsItsLongReadTimeoutFromCurrentPrefix() throws IOException {
        StandardEnvironment environment = environment("docs/nacos/codecoachai-interview-dev.yml");
        FeignClientProperties properties = bind(environment);

        FeignClientProperties.FeignClientConfiguration defaults =
                properties.getConfig().get("default");
        assertEquals(3000, defaults.getConnectTimeout());
        assertEquals(40000, defaults.getReadTimeout());
        assertFalse(environment.containsProperty("feign.client.config.default.readTimeout"));
    }

    private FeignClientProperties bind(StandardEnvironment environment) {
        return Binder.get(environment)
                .bind("spring.cloud.openfeign.client", Bindable.of(FeignClientProperties.class))
                .orElseThrow(() -> new AssertionError("OpenFeign client properties did not bind"));
    }

    private StandardEnvironment environment(String relativePath) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve(relativePath))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }

        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> propertySource : loader.load(
                relativePath,
                new FileSystemResource(root.resolve(relativePath)))) {
            environment.getPropertySources().addFirst(propertySource);
        }
        return environment;
    }
}
