package com.codecoachai.question.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codecoachai.question.feign.AiPracticeFeignClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class PracticeReviewFeignClientConfigContractTest {

    private static final String COMMON_NACOS_CONFIG = "docs/nacos/codecoachai-common-dev.yml";

    @Test
    void practiceReviewUsesItsNamedLongRunningFeignConfiguration() {
        FeignClient annotation = AiPracticeFeignClient.class.getAnnotation(FeignClient.class);

        assertEquals("codecoachai-ai", annotation.name());
        assertEquals("aiPracticeFeignClient", annotation.contextId());
    }

    @Test
    void commonNacosConfigKeepsPracticeReviewOutsideTheFiveSecondDefault() throws IOException {
        Path configPath = repositoryRoot().resolve(COMMON_NACOS_CONFIG);
        String config = Files.readString(configPath);

        assertTrue(hasTimeoutBlock(config, "aiPracticeFeignClient", 120000));
        assertTrue(hasTimeoutBlock(config, "default", 5000));

        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("codecoachai-common", new FileSystemResource(configPath))
                .forEach(environment.getPropertySources()::addFirst);
        FeignClientProperties properties = Binder.get(environment)
                .bind("spring.cloud.openfeign.client", Bindable.of(FeignClientProperties.class))
                .orElseThrow(() -> new IllegalStateException("OpenFeign client properties were not bound"));
        FeignClientProperties.FeignClientConfiguration practiceConfig =
                properties.getConfig().get("aiPracticeFeignClient");

        assertTrue(practiceConfig != null,
                () -> "Bound Feign client keys were " + properties.getConfig().keySet());
        assertEquals(3000, practiceConfig.getConnectTimeout());
        assertEquals(120000, practiceConfig.getReadTimeout());
    }

    private boolean hasTimeoutBlock(String config, String clientName, int readTimeout) {
        Pattern block = Pattern.compile(
                "(?m)^\\s*" + Pattern.quote(clientName) + ":\\R"
                        + "\\s*connectTimeout:\\s*3000\\R"
                        + "\\s*readTimeout:\\s*" + readTimeout + "\\s*$");
        return block.matcher(config).find();
    }

    private static Path repositoryRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null) {
            Path root = Path.of(multiModuleRoot).toAbsolutePath().normalize();
            if (Files.exists(root.resolve(COMMON_NACOS_CONFIG))) {
                return root;
            }
        }
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(COMMON_NACOS_CONFIG))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate codecoch-ai-java repository root");
        }
        return current;
    }
}
