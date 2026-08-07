package com.codecoachai.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "codecoachai.interview.voice")
public class InterviewVoiceProviderProperties {

    private Provider tts = new Provider();
    private StreamingAsr streamingAsr = new StreamingAsr();

    @Data
    public static class Provider {
        private String provider;
    }

    @Data
    public static class StreamingAsr {
        private String provider;
    }
}
