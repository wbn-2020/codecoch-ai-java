package com.codecoachai.common.vector.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "codecoachai.vector")
public class VectorStoreProperties {

    private boolean enabled = false;

    private String provider = "qdrant";

    private String baseUrl = "http://127.0.0.1:6333";

    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration requestTimeout = Duration.ofSeconds(15);

    private int defaultLimit = 10;
}
