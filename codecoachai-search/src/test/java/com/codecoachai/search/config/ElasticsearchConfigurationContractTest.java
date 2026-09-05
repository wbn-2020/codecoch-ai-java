package com.codecoachai.search.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

class ElasticsearchConfigurationContractTest {

    @Test
    void productionCredentialsBindToBusinessClientAndActuatorClient() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test-environment",
                Map.of(
                        "ELASTICSEARCH_URIS", "http://elasticsearch:9200",
                        "ELASTICSEARCH_USERNAME", "elastic",
                        "ELASTIC_PASSWORD", "test-secret")));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application-yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        Binder binder = Binder.get(environment);
        ElasticsearchProperties actuatorProperties = binder.bind(
                "spring.elasticsearch", Bindable.of(ElasticsearchProperties.class)).get();
        ElasticsearchConfig businessProperties = binder.bind(
                "codecoachai.elasticsearch", Bindable.of(ElasticsearchConfig.class)).get();

        assertEquals("http://elasticsearch:9200", actuatorProperties.getUris().get(0));
        assertEquals("elastic", actuatorProperties.getUsername());
        assertEquals("test-secret", actuatorProperties.getPassword());
        assertEquals("http://elasticsearch:9200", businessProperties.getUris());
        assertEquals("elastic", businessProperties.getUsername());
        assertEquals("test-secret", businessProperties.getPassword());
    }

    @Test
    void deploymentConfigKeepsBothClientsOnTheAuthenticatedContainerEndpoint() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Yaml yaml = new Yaml();

        Map<String, Object> compose = yaml.load(Files.readString(repositoryRoot.resolve("docker-compose.yml")));
        Map<String, Object> services = mapping(compose.get("services"));
        Map<String, Object> search = mapping(services.get("codecoachai-search"));
        Map<String, Object> environment = mapping(search.get("environment"));
        Map<String, Object> elasticsearch = mapping(services.get("elasticsearch"));
        Map<String, Object> elasticsearchEnvironment = mapping(elasticsearch.get("environment"));

        assertEquals(
                "${ELASTIC_PASSWORD:-codecoachai-local-elastic}",
                elasticsearchEnvironment.get("ELASTIC_PASSWORD"));
        assertEquals(
                "${ELASTICSEARCH_URIS:-http://elasticsearch:9200}",
                environment.get("ELASTICSEARCH_URIS"));
        assertEquals("${ELASTICSEARCH_USERNAME:-elastic}", environment.get("ELASTICSEARCH_USERNAME"));
        assertEquals(
                "${ELASTIC_PASSWORD:-codecoachai-local-elastic}",
                environment.get("ELASTIC_PASSWORD"));
        assertEquals(
                "${ELASTICSEARCH_URIS:-http://elasticsearch:9200}",
                environment.get("SPRING_ELASTICSEARCH_URIS"));
        assertEquals("${ELASTICSEARCH_USERNAME:-elastic}", environment.get("SPRING_ELASTICSEARCH_USERNAME"));
        assertEquals(
                "${ELASTIC_PASSWORD:-codecoachai-local-elastic}",
                environment.get("SPRING_ELASTICSEARCH_PASSWORD"));
        assertEquals(
                "${ELASTICSEARCH_URIS:-http://elasticsearch:9200}",
                environment.get("CODECOACHAI_ELASTICSEARCH_URIS"));
        assertEquals(
                "${ELASTICSEARCH_USERNAME:-elastic}",
                environment.get("CODECOACHAI_ELASTICSEARCH_USERNAME"));
        assertEquals(
                "${ELASTIC_PASSWORD:-codecoachai-local-elastic}",
                environment.get("CODECOACHAI_ELASTICSEARCH_PASSWORD"));

        Map<String, Object> releaseCompose =
                yaml.load(Files.readString(repositoryRoot.resolve("docker-compose.release.yml")));
        Map<String, Object> releaseSearch =
                mapping(mapping(releaseCompose.get("services")).get("codecoachai-search"));
        Map<String, Object> releaseEnvironment = mapping(releaseSearch.get("environment"));
        assertEquals(
                "${ELASTICSEARCH_URIS:?ELASTICSEARCH_URIS is required for release}",
                releaseEnvironment.get("SPRING_ELASTICSEARCH_URIS"));
        assertEquals(
                "${ELASTIC_PASSWORD:?ELASTIC_PASSWORD is required for release}",
                releaseEnvironment.get("SPRING_ELASTICSEARCH_PASSWORD"));
        assertEquals(
                "${ELASTIC_PASSWORD:?ELASTIC_PASSWORD is required for release}",
                releaseEnvironment.get("CODECOACHAI_ELASTICSEARCH_PASSWORD"));
        assertEquals(
                "${ELASTIC_PASSWORD:?ELASTIC_PASSWORD is required for release}",
                releaseEnvironment.get("ELASTIC_PASSWORD"));

        Map<String, Object> nacos = yaml.load(Files.readString(
                repositoryRoot.resolve("docs/nacos/codecoachai-search-dev.yml")));
        Map<String, Object> springElasticsearch = mapping(mapping(nacos.get("spring")).get("elasticsearch"));
        Map<String, Object> businessElasticsearch =
                mapping(mapping(nacos.get("codecoachai")).get("elasticsearch"));

        assertEquals(
                "${ELASTICSEARCH_URIS:http://elasticsearch:9200}",
                springElasticsearch.get("uris"));
        assertEquals("elastic", springElasticsearch.get("username"));
        assertEquals("${ELASTIC_PASSWORD}", springElasticsearch.get("password"));
        assertEquals(
                "${ELASTICSEARCH_URIS:http://elasticsearch:9200}",
                businessElasticsearch.get("uris"));
        assertEquals("elastic", businessElasticsearch.get("username"));
        assertEquals("${ELASTIC_PASSWORD}", businessElasticsearch.get("password"));
    }

    private static Path findRepositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isRegularFile(candidate.resolve("docker-compose.yml"))) {
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
}
