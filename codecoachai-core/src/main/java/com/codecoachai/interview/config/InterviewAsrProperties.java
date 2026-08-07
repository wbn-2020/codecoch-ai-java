package com.codecoachai.interview.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.interview.asr")
public class InterviewAsrProperties {

    private boolean enabled = false;
    private String provider = "HTTP_ASR";
    private String endpoint;
    private String apiKey;
    private String authHeader = "Authorization";
    private String authScheme = "Bearer";
    private String model;
    private RequestMode requestMode = RequestMode.MULTIPART;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
    private Duration maxAudioDuration = Duration.ofMinutes(2);
    private long maxAudioBytes = 10 * 1024 * 1024L;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Multipart multipart = new Multipart();
    private Json json = new Json();

    public enum RequestMode {
        MULTIPART,
        JSON
    }

    @Data
    public static class Multipart {
        private String fileField = "file";
        private String languageField = "language";
        private String sceneField = "scene";
        private String requestIdField = "requestId";
        private String traceIdField = "traceId";
        private String modelField = "model";
    }

    @Data
    public static class Json {
        private long maxAudioBytes = 2 * 1024 * 1024L;
        private String audioField = "audioBase64";
        private String mimeTypeField = "mimeType";
        private String languageField = "language";
        private String sceneField = "scene";
        private String requestIdField = "requestId";
        private String traceIdField = "traceId";
        private String modelField = "model";
    }
}
